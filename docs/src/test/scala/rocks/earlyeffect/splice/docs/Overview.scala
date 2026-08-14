package rocks.earlyeffect.splice.docs

import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

object Overview extends DocSpecSuite:

  def doc = page("Overview")(
    md"""
**sbt-splice** is an sbt 2 / Scala 3 plugin that takes Scala.js linker output and
emits browser-loadable JavaScript with bare module specifiers resolved. It never
invokes npm, npx, or node, and it never reads a `package.json`.

JS libraries arrive as pinned bytes: a file you vendor, a Maven/WebJar coordinate,
or a fetch from jsDelivr / unpkg through Coursier. The plugin is general-purpose.
Any `@JSImport("some-lib")` (or CommonJS `require`) is in scope.
""",
    section("Fast vs full")(
      md"""
`spliceFast` runs after `fastLinkJS` (development, seconds, readable enough).
`spliceFull` runs after `fullLinkJS` and is the production path (small, efficient).
Neither task reimplements the Scala.js linker.
""",
      exampleValue {
        List("spliceFast" -> "fastLinkJS", "spliceFull" -> "fullLinkJS")
      }.assert { pairs =>
        assertTrue(
          pairs.head == ("spliceFast" -> "fastLinkJS"),
          pairs.last == ("spliceFull" -> "fullLinkJS"),
        )
      },
    ),
    section("Status")(
      md"""
Phase 3 maps specifiers, then `spliceFull` runs Closure advanced on the combined
file (same `closure-compiler` artifact Scala.js 1.22 pins: `v20220202`). See
[ROADMAP.md](https://github.com/early-effect/sbt-splice/blob/main/ROADMAP.md)
for a real-library run (Phase 4).
"""
    ),
  )
end Overview
