package rocks.earlyeffect.splice

import zio.*
import zio.test.*

import java.nio.file.{Files, Path}
import java.security.MessageDigest

object SpliceSpec extends ZIOSpecDefault:

  def spec =
    suite("Splice")(
      test("unresolved bare specifier names the specifier and referring file") {
        for
          out <- tempOut
          err <- Splice
            .run(
              SpliceInput(
                linker = List(LinkerFile("main.js", """import * as foo from "foo";""")),
                libs = Map.empty,
                output = out,
              )
            )
            .flip
        yield assertTrue(
          err == SpliceError.Unresolved("foo", "main.js"),
          err.message == """sbt-splice: unresolved specifier "foo" in main.js""",
        )
      },
      test("missing mapped file names the specifier and path") {
        for
          out <- tempOut
          missing = Path.of("/no/such/foo.js")
          err <- Splice
            .run(
              SpliceInput(
                linker = List(LinkerFile("main.js", "")),
                libs = Map("foo" -> missing),
                output = out,
              )
            )
            .flip
        yield assertTrue(err == SpliceError.MissingFile("foo", missing.toString))
      },
      test("splices a namespace import so the mapped specifier does not remain") {
        for
          dir <- tempDir
          foo = dir.resolve("foo.js")
          _ <- write(foo, """export function greet() { return "ok"; }""")
          out = dir.resolve("splice.js")
          path <- Splice.run(
            SpliceInput(
              linker = List(LinkerFile("main.js", "const Foo = __splice_foo;\n")),
              libs = Map("foo" -> foo),
              output = out,
            )
          )
          body <- ZIO.attempt(Files.readString(path))
        yield assertTrue(
          path == out,
          body.contains("const Foo = __splice_foo;"),
          body.contains("exports.greet = function greet"),
          !body.contains("""from "foo""""),
          !body.contains("""require("foo")"""),
        )
      },
      test("source map section offset equals prepended wrapper line count including blanks") {
        for
          dir <- tempDir
          foo = dir.resolve("foo.js")
          _ <- write(foo, "export function greet() { return \"ok\"; }\n")
          linkerMap = dir.resolve("main.js.map")
          _ <- write(linkerMap, """{"version":3,"file":"main.js","sources":["Hello.scala"],"mappings":"AAAA"}""")
          out    = dir.resolve("splice.js")
          marker = "const Foo = __splice_foo;\n"
          _ <- Splice.run(
            SpliceInput(
              linker = List(LinkerFile("main.js", marker, Some(linkerMap))),
              libs = Map("foo" -> foo),
              output = out,
              sourceMaps = true,
            )
          )
          body <- ZIO.attempt(Files.readString(out))
          map  <- ZIO.attempt(Files.readString(SourceMaps.mapPath(out)))
          idx    = body.indexOf(marker)
          prefix = body.substring(0, idx)
          offset = SourceMaps.lineOffset(prefix)
        yield assertTrue(
          idx > 0,
          offset > 0,
          map.contains(s""""line":$offset"""),
          body.contains("sourceMappingURL=splice.js.map"),
        )
      },
      test("splices two mapped specifiers into one file") {
        for
          dir <- tempDir
          foo = dir.resolve("foo.js")
          bar = dir.resolve("bar.js")
          _ <- write(foo, """export function foo() { return 1; }""")
          _ <- write(bar, """export function bar() { return 2; }""")
          out = dir.resolve("splice.js")
          _ <- Splice.run(
            SpliceInput(
              linker = List(
                LinkerFile(
                  "main.js",
                  """const Foo = __splice_foo;
                    |const Bar = __splice_bar;
                    |""".stripMargin,
                )
              ),
              libs = Map("foo" -> foo, "bar" -> bar),
              output = out,
            )
          )
          body <- ZIO.attempt(Files.readString(out))
        yield assertTrue(
          body.contains("__splice_foo"),
          body.contains("__splice_bar"),
          !body.contains("""from "foo""""),
          !body.contains("""from "bar""""),
        )
      },
      test("resolves nested relative imports against the mapped file") {
        for
          dir <- tempDir
          util = dir.resolve("util.js")
          foo  = dir.resolve("foo.js")
          _ <- write(util, """export function helper() { return "ok"; }""")
          _ <- write(
            foo,
            """import { helper } from "./util.js";
                              |export function greet() { return helper(); }
                              |""".stripMargin,
          )
          out = dir.resolve("splice.js")
          _ <- Splice.run(
            SpliceInput(
              linker = List(LinkerFile("main.js", "const Foo = __splice_foo;\n")),
              libs = Map("foo" -> foo),
              output = out,
            )
          )
          body <- ZIO.attempt(Files.readString(out))
        yield assertTrue(
          body.contains("helper"),
          !body.contains("""from "./util.js""""),
          !body.contains("""from "foo""""),
        )
      },
      test("splices pinned escape-string-regexp 5.0.0 and keeps the published sha256") {
        val jsBytes = vendorBytes("escape-string-regexp@5.0.0.js")
        val pinned  =
          String(vendorBytes("escape-string-regexp@5.0.0.js.sha256"), java.nio.charset.StandardCharsets.UTF_8).trim
        val digest = sha256(jsBytes)
        for
          dir <- tempDir
          js = dir.resolve("escape-string-regexp.js")
          _ <- write(js, String(jsBytes, java.nio.charset.StandardCharsets.UTF_8))
          out = dir.resolve("splice.js")
          _ <- Splice.run(
            SpliceInput(
              linker = List(
                LinkerFile(
                  "main.js",
                  """const escapeStringRegexp = __splice_escape_string_regexp.default;
                    |globalThis.__spliced = escapeStringRegexp("hello?");
                    |""".stripMargin,
                )
              ),
              libs = Map("escape-string-regexp" -> js),
              output = out,
            )
          )
          body <- ZIO.attempt(Files.readString(out))
        yield assertTrue(
          digest == pinned,
          pinned == "af2065ad2f2d2b91946c2121e21618daa3f4b18787af9226f8c953ca54cca2f5",
          body.contains("exports.default = function escapeStringRegexp"),
          body.contains("const escapeStringRegexp = __splice_escape_string_regexp.default;"),
          !body.contains("""from "escape-string-regexp""""),
        )
        end for
      },
      test("optimize drops unused spliced exports and leftover module syntax") {
        for
          dir <- tempDir
          foo = dir.resolve("foo.js")
          _ <- write(
            foo,
            """export function used() { return 1; }
              |export function unused() { return "DEAD_CODE_MARKER"; }
              |""".stripMargin,
          )
          out = dir.resolve("splice.js")
          _ <- Splice.run(
            SpliceInput(
              linker = List(
                LinkerFile(
                  "main.js",
                  """const used = __splice_foo.used;
                    |used();
                    |export { used };
                    |""".stripMargin,
                )
              ),
              libs = Map("foo" -> foo),
              output = out,
              optimize = true,
            )
          )
          body <- ZIO.attempt(Files.readString(out))
        yield assertTrue(
          !body.contains("DEAD_CODE_MARKER"),
          !body.contains("export "),
          !body.contains("""from "foo""""),
        )
      },
      test("optimize fails the program when Closure cannot parse a spliced file") {
        for
          dir <- tempDir
          foo = dir.resolve("foo.js")
          _ <- write(foo, "const x = {")
          out = dir.resolve("splice.js")
          err <- Splice
            .run(
              SpliceInput(
                linker = List(LinkerFile("main.js", "const Foo = __splice_foo;\n")),
                libs = Map("foo" -> foo),
                output = out,
                optimize = true,
              )
            )
            .flip
        yield assertTrue(
          err match
            case SpliceError.Closure(detail) =>
              detail.nonEmpty && err.message.startsWith("sbt-splice: Closure compiler failed:")
            case _ => false
        )
      },
      test("wraps CJS without rewriting exports and the binding is callable") {
        for
          dir <- tempDir
          foo = dir.resolve("foo.js")
          _ <- write(foo, """module.exports.greet = function greet() { return "cjs"; };""")
          out = dir.resolve("splice.js")
          _ <- Splice.run(
            SpliceInput(
              linker = List(
                LinkerFile(
                  "main.js",
                  """const Foo = __splice_foo;
                    |document.getElementById("out").textContent = Foo.greet();
                    |""".stripMargin,
                )
              ),
              libs = Map("foo" -> foo),
              output = out,
            )
          )
          body <- ZIO.attempt(Files.readString(out))
        yield assertTrue(
          body.contains("module.exports.greet"),
          !body.contains("export "),
          JsHost.evalExpr(body, "document.getElementById('out').textContent") == "cjs",
        )
      },
      test("wraps UMD through the CJS branch") {
        val umd =
          """(function (root, factory) {
            |  if (typeof exports === "object" && typeof module !== "undefined") module.exports = factory();
            |  else root.umdFoo = factory();
            |}(typeof globalThis !== "undefined" ? globalThis : this, function () {
            |  return { greet: function greet() { return "umd"; } };
            |}));
            |""".stripMargin
        for
          dir <- tempDir
          foo = dir.resolve("foo.js")
          _ <- write(foo, umd)
          out = dir.resolve("splice.js")
          _ <- Splice.run(
            SpliceInput(
              linker = List(
                LinkerFile(
                  "main.js",
                  """const Foo = __splice_foo;
                    |document.getElementById("out").textContent = Foo.greet();
                    |""".stripMargin,
                )
              ),
              libs = Map("foo" -> foo),
              output = out,
            )
          )
          body <- ZIO.attempt(Files.readString(out))
        yield assertTrue(
          JsKind.classify(umd) == JsKind.Umd,
          JsHost.evalExpr(body, "document.getElementById('out').textContent") == "umd",
        )
        end for
      },
      test("refuses export star from") {
        for
          dir <- tempDir
          foo = dir.resolve("foo.js")
          _ <- write(foo, """export * from "./bar.js";""")
          out = dir.resolve("splice.js")
          err <- Splice
            .run(
              SpliceInput(
                linker = List(LinkerFile("main.js", """import * as Foo from "foo";""")),
                libs = Map("foo" -> foo),
                output = out,
              )
            )
            .flip
        yield assertTrue(
          err match
            case SpliceError.Unwrappable(file, reason) =>
              file == "foo.js" && reason.contains("export * from")
            case _ => false
        )
      },
      test("extern libs still wrap for fast and survive Closure unused-code DCE") {
        for
          dir <- tempDir
          foo = dir.resolve("foo.js")
          _ <- write(
            foo,
            """export function used() { return 1; }
              |export function unused() { return "EXTERN_DEAD_CODE"; }
              |""".stripMargin,
          )
          out = dir.resolve("splice.js")
          _ <- Splice.run(
            SpliceInput(
              linker = List(
                LinkerFile(
                  "main.js",
                  """const used = __splice_foo.used;
                    |used();
                    |""".stripMargin,
                )
              ),
              libs = Map("foo" -> foo),
              output = out,
              optimize = true,
              extern = Set("foo"),
            )
          )
          body <- ZIO.attempt(Files.readString(out))
        yield assertTrue(
          body.contains("EXTERN_DEAD_CODE"),
          body.contains("__splice_foo"),
        )
      },
      test("spliceFull with classComponent keepProperties calls the Scala.js render override") {
        for
          dir <- tempDir
          foo = dir.resolve("foo.js")
          _ <- write(foo, classComponentEsm)
          out = dir.resolve("splice.js")
          _ <- Splice.run(
            SpliceInput(
              linker = List(LinkerFile("main.js", scalaJsSubclassLinker)),
              libs = Map("foo" -> foo),
              output = out,
              optimize = true,
              keepProperties = Splice.classComponent.toSet,
            )
          )
          body <- ZIO.attempt(Files.readString(out))
        yield assertTrue(JsHost.evalExpr(body, "document.getElementById('out').textContent") == "OVERRIDE_RENDER")
      },
      test("spliceFull without keepProperties does not call a Scala.js class-extends render override") {
        for
          dir <- tempDir
          foo = dir.resolve("foo.js")
          _ <- write(foo, classComponentEsm)
          out = dir.resolve("splice.js")
          _ <- Splice.run(
            SpliceInput(
              linker = List(LinkerFile("main.js", scalaJsSubclassLinker)),
              libs = Map("foo" -> foo),
              output = out,
              optimize = true,
            )
          )
          body <- ZIO.attempt(Files.readString(out))
        yield assertTrue(JsHost.evalExpr(body, "document.getElementById('out').textContent") == "BASE_RENDER")
      },
      test("keep unions property names onto every SpliceLib shape") {
        val file = Splice.file("foo", new java.io.File("foo.js")).keep("render").keep("props")
        val cdn  = Splice.lib("foo", "1.0.0", "foo.js").keep(Splice.classComponent*)
        assertTrue(
          file.keepProperties == Set("render", "props"),
          cdn.keepProperties.contains("render"),
          cdn.keepProperties.contains("componentDidMount"),
        )
      },
    )

  private def vendorBytes(name: String): Array[Byte] =
    val in = Option(getClass.getResourceAsStream(s"/vendor/$name"))
      .getOrElse(sys.error(s"missing resource /vendor/$name"))
    try in.readAllBytes()
    finally in.close()

  private def sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString

  private def tempDir: UIO[Path] =
    ZIO.attempt(Files.createTempDirectory("sbt-splice-")).orDie

  private def tempOut: UIO[Path] =
    tempDir.map(_.resolve("out.js"))

  private def write(path: Path, body: String): Task[Unit] =
    ZIO.attempt {
      Files.writeString(path, body)
      ()
    }

  private val classComponentEsm =
    """function Component() {}
      |Component.prototype.render = function () { return "BASE_RENDER"; };
      |function mount(type) {
      |  var h = new type();
      |  return h.render();
      |}
      |export { Component, mount };
      |""".stripMargin

  private val scalaJsSubclassLinker =
    """globalThis["__sbt_splice_extends"] = function (a) {
      |  var $superClass = a;
      |  return class extends $superClass {
      |    ["render"]() { return "OVERRIDE_RENDER"; }
      |  };
      |};
      |var C = globalThis["__sbt_splice_extends"](__splice_foo.Component);
      |document.getElementById("out").textContent = __splice_foo.mount(C);
      |""".stripMargin
end SpliceSpec
