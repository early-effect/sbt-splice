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
or a fetch from jsDelivr / unpkg / a GitHub tag tarball through Coursier. The plugin
is general-purpose.
Any `@JSImport("some-lib")` (or CommonJS `require`) is in scope.
""",
    section("Fast vs full")(
      md"""
`spliceFast` private-links remapped IR (development, seconds, readable enough).
`spliceFull` private-links remapped IR and is the production path (small, efficient).
Vanilla `fastLinkJS` / `fullLinkJS` are unchanged; splice does not rewrite their JS.
""",
      exampleValue {
        List("spliceFast" -> "private remapped link", "spliceFull" -> "private remapped link + Closure")
      }.assert { pairs =>
        assertTrue(
          pairs.head._1 == "spliceFast",
          pairs.last._1 == "spliceFull",
        )
      },
    ),
    section("Status")(
      md"""
Phase 4 maps specifiers and **runs** the spliced file: `spliceFast` and
`spliceFull` of a real `@JSImport("preact")` execute on GraalJS (JVM, no Node).
See [ROADMAP.md](https://github.com/early-effect/sbt-splice/blob/main/ROADMAP.md)
for first-consumer adoption.
"""
    ),
  )
end Overview
