package rocks.earlyeffect.splice

import zio.test.*

import java.nio.file.Path

object SourceMapsSpec extends ZIOSpecDefault:

  def spec =
    suite("SourceMaps")(
      test("lineOffset counts every newline including blanks") {
        assertTrue(
          SourceMaps.lineOffset("") == 0,
          SourceMaps.lineOffset("a") == 0,
          SourceMaps.lineOffset("a\n") == 1,
          SourceMaps.lineOffset("a\n\n") == 2,
          SourceMaps.lineOffset("a\n\nb\n") == 3,
        )
      },
      test("indexed map first section offset is the prepended line count") {
        val prefix = "const __splice_foo = (() => {\n  return {};\n})();\n\n"
        val map    = Path.of("/tmp/splice/fast-link/main.js.map")
        val dest   = Path.of("/tmp/splice/fast.js.map")
        val linker = List(LinkerFile("main.js", "const Foo = __splice_foo;\n", Some(map)))
        val secs   = SourceMaps.sectionsFor(prefix, linker, dest)
        val json   = SourceMaps.indexed("fast.js", secs)
        assertTrue(
          secs.head.line == SourceMaps.lineOffset(prefix),
          secs.head.line == 4,
          secs.head.url == "fast-link/main.js.map",
          json.contains("\"line\":4"),
          json.contains("fast-link/main.js.map"),
        )
      },
    )
end SourceMapsSpec
