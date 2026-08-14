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
          got.length < vendor.length + linker.length,
          !got.contains("DEAD_CODE_MARKER"),
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
          got.startsWith("const __splice_ext"),
          got.contains("EXTERN_KEEP"),
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
