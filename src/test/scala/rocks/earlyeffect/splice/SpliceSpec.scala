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
              linker = List(LinkerFile("main.js", """import * as Foo from "foo";""")),
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
                  """import * as Foo from "foo";
                    |import * as Bar from "bar";
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
              linker = List(LinkerFile("main.js", """import * as Foo from "foo";""")),
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
                  """import escapeStringRegexp from "escape-string-regexp";
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
end SpliceSpec
