package rocks.earlyeffect.splice

import zio.*
import zio.test.*

import java.nio.file.{Files, Path}

object ClosureSpec extends ZIOSpecDefault:

  def spec =
    suite("Closure")(
      test("programDigest is stable and changes when linker or vendor bytes change") {
        for
          dir <- tempDir
          lib = dir.resolve("foo.js")
          _ <- write(lib, "export const x = 1;")
          out    = dir.resolve("full.js")
          linker = List(LinkerFile("main.js", "const n = 1;"))
          libs   = Map("foo" -> lib)
          a      = Closure.programDigest(linker, libs, out)
          b      = Closure.programDigest(linker, libs, out)
          c      = Closure.programDigest(List(LinkerFile("main.js", "const n = 2;")), libs, out)
          _ <- write(lib, "export const x = 2;")
          d = Closure.programDigest(linker, libs, out)
        yield assertTrue(a == b, a != c, a != d, a.length == 64)
      },
      test("cacheHit is true only when stamp, digest, and output all match") {
        for
          dir <- tempDir
          out   = dir.resolve("full.js")
          stamp = dir.resolve("digest")
          _ <- write(out, "js")
          _ <- ZIO.succeed(Closure.storeCache(stamp, "abc"))
        yield assertTrue(
          Closure.cacheHit(stamp, "abc", out),
          !Closure.cacheHit(stamp, "other", out),
          !Closure.cacheHit(stamp, "abc", dir.resolve("missing.js")),
          !Closure.cacheHit(stamp, "abc", out, Some(dir.resolve("missing.js.map"))),
        )
      },
      test("optimize is smaller than the concatenated inputs") {
        val vendor =
          """const __splice_foo = (() => {
            |  const module = { exports: {} };
            |  const exports = module.exports;
            |  exports.used = function used() { return 1; };
            |  exports.unused = function unused() { return "DEAD_CODE_MARKER"; };
            |  return module.exports;
            |})();
            |""".stripMargin
        val linker = "const n = __splice_foo.used();"
        for got <- Closure.optimize(List("vendor.js" -> vendor, "linker.js" -> linker))
        yield assertTrue(
          got.js.length < vendor.length + linker.length,
          !got.js.contains("DEAD_CODE_MARKER"),
        )
      },
      test("rewriteScalaJsNames replaces escape and raw U+FF3F") {
        val escaped = """$m_Ljava_nio_charset_ISO\uff3f8859\uff3f1$"""
        val upper   = """$m_Ljava_nio_charset_ISO\uFF3F8859\uFF3F1$"""
        val raw     =
          s"$$m_Ljava_nio_charset_ISO${Closure.FullwidthLowLine}8859${Closure.FullwidthLowLine}1$$"
        val standIn = "$m_Ljava_nio_charset_ISO$uFF3F8859$uFF3F1$"
        assertTrue(
          Closure.rewriteScalaJsNames(escaped) == standIn,
          Closure.rewriteScalaJsNames(upper) == standIn,
          Closure.rewriteScalaJsNames(raw) == standIn,
        )
      },
      test("optimize accepts Scala.js minify names and keeps host free-vars") {
        val linker =
          """function $m_Ljava_nio_charset_ISO\uff3f8859\uff3f1$() { return 1; }
            |onmessage = function () { return $m_Ljava_nio_charset_ISO\uff3f8859\uff3f1$(); };
            |attachEvent("onmessage", onmessage);
            |""".stripMargin
        for got <- Closure.optimize(List("linker.js" -> linker))
        yield assertTrue(
          !got.js.contains("\\uff3f"),
          !got.js.contains("\\uFF3F"),
          !got.js.contains(Closure.FullwidthLowLine.toString),
          got.js.contains("onmessage"),
          got.js.contains("attachEvent"),
        )
      },
      test("prefix chunks are prepended and extra extern names are not minified away") {
        val prefix =
          """const __splice_ext = (() => {
            |  const module = { exports: {} };
            |  module.exports.keep = function keep() { return "EXTERN_KEEP"; };
            |  return module.exports;
            |})();
            |""".stripMargin
        val linker = "const n = __splice_ext.keep();"
        for got <- Closure.optimize(
            inputs = List("linker.js" -> linker),
            prefix = List("ext.js" -> prefix),
            extraExterns = List("__splice_ext"),
          )
        yield assertTrue(
          got.js.startsWith("const __splice_ext"),
          got.js.contains("EXTERN_KEEP"),
        )
      },
      test("optimize stubs process so ZIO-shaped exitCode and env eval without a host process") {
        val linker =
          """onmessage = function () {
            |  process.exitCode = 7;
            |  var env = (typeof process !== "undefined" && typeof process.env !== "undefined")
            |    ? process.env : {};
            |  return String(process.exitCode) + (env.FOO || "none");
            |};
            |""".stripMargin
        for
          got <- Closure.optimize(List("linker.js" -> linker))
          ran  = JsHost.evalExpr(got.js, "onmessage()")
          host = JsHost.evalExpr(got.js, "typeof process")
        yield assertTrue(ran == "7none", host == "undefined")
      },
      test("optimize fails on a free-var that is not a host extern or Node stub") {
        val linker = "onmessage = function () { notAHostApi.foo = 1; };"
        for result <- Closure.optimize(List("linker.js" -> linker)).either
        yield assertTrue(
          result match
            case Left(SpliceError.Closure(detail)) => detail.contains("notAHostApi")
            case _                                 => false
        )
      },
    )

  private def tempDir: UIO[Path] =
    ZIO.attempt(Files.createTempDirectory("sbt-splice-closure-")).orDie

  private def write(path: Path, body: String): Task[Unit] =
    ZIO.attempt {
      Files.writeString(path, body)
      ()
    }
end ClosureSpec
