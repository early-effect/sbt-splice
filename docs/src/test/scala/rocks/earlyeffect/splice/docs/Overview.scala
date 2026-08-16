package rocks.earlyeffect.splice.docs

import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

object Overview extends DocSpecSuite:

  def doc = page("Overview")(
    md"""
Scala.js lets you write `import "preact"` from Scala, usually with `@JSImport("preact")` (or `require` for CommonJS).
The linker then emits a JavaScript file that still says `import "preact"`.

A browser cannot resolve that name. There is no `node_modules` folder next to the file, and the browser does not
ask npm what `"preact"` means. The leftover name is a **specifier**: the string `@JSImport` used, which must become
real bytes before the page can load.

**sbt-splice** is how you name those bytes. You point each specifier at a file you copied into the repo, a
**WebJar** (the same file published on Maven), or a **pin** (a CDN or GitHub download whose version and **sha256**
checksum you wrote down). Then `spliceFast` (development) or `spliceFull` (production) writes **one** JavaScript
file you can put in a `<script>` tag.
""",
    section("Fast vs full")(
      md"""
`spliceFast` is the development build: readable enough, usually seconds. `spliceFull` is the production build: the
same libraries, then **Closure** (a JVM minifier) so the file is small. Vanilla Scala.js `fastLinkJS` / `fullLinkJS`
are unchanged; splice writes its own output next to them.
""",
      exampleValue {
        List("spliceFast" -> "development", "spliceFull" -> "production + Closure")
      }.assert { pairs =>
        assertTrue(
          pairs.head._1 == "spliceFast",
          pairs.last._1 == "spliceFull",
        )
      },
    ),
    section("If you already use Node")(
      md"""
Skip this note if you are not coming from webpack, Vite, or npm. Those tools start from a `package.json` and a
`node_modules` tree. Splice does not. It never runs npm, never reads `package.json`, and never leaves `import "preact"`
for a bundler to fix. If you already have a Node pipeline, splice is a different path, not a plugin for that pipeline.
"""
    ),
  )
end Overview
