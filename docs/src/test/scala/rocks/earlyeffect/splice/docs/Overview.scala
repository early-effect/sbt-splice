package rocks.earlyeffect.splice.docs

import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

object Overview extends DocSpecSuite:

  def doc = page("Overview")(
    md"""
You do not install Node, Vite, or esbuild. `spliceFull` is the production bundle Scala.js 1.21 asked you to get from
Vite: type-aware Scala minify, then a general JS minifier that leaves protocol names (`render`, `setState`) and
`class extends Error` alone. The minifier is a pinned esbuild binary. First `spliceFull` fetches it through Coursier
for this OS/arch, checks sha256, and runs it. A clone of the project is enough. JDK and sbt were already required.

Scala.js lets you write `import "preact"` from Scala, usually with `@JSImport("preact")` (or `require` for CommonJS).
The linker then emits a JavaScript file that still says `import "preact"`. A browser cannot resolve that name. There
is no `node_modules` folder, and the browser does not ask npm what `"preact"` means. The leftover name is a
**specifier**. **sbt-splice** points each specifier at pinned bytes (a file in the repo, a **WebJar**, or a CDN/GitHub
download with **sha256**) and writes **one** JavaScript file for a `<script>` tag.

If the program has no npm imports, leave `spliceLibs` empty. `spliceFull` is still the production file. `NoModule` is
the natural linker kind in that case.
""",
    section("Fast vs full")(
      md"""
`spliceFast` is development: concat, usually seconds. `spliceFull` is production: Scala.js minify, then pinned
esbuild so the file is small. That pass runs even when `spliceLibs` is empty. `spliceClosure` is optional Closure
advanced (unused-vendor DCE). Vanilla Scala.js `fastLinkJS` / `fullLinkJS` are unchanged; splice writes its own
output next to them.
""",
      exampleValue {
        List("spliceFast" -> "development", "spliceFull" -> "production minify", "spliceClosure" -> "optional Closure")
      }.assert { pairs =>
        assertTrue(
          pairs.head._1 == "spliceFast",
          pairs(1)._1 == "spliceFull",
          pairs.last._1 == "spliceClosure",
        )
      },
    ),
    section("If you already use Vite")(
      md"""
You can drop Node from the Scala.js production path. Vite's minify leaves JS property names alone and keeps
`class` / `super()`. So does `spliceFull`. What you do not get is Vite's ESM tree-shaker walking `node_modules`.
Splice never reads `package.json`, never runs npm, and never leaves `import "preact"` for a bundler to fix.
Libraries are sealed pins. The output is one `<script>` file.

How that compares to Closure, and why property renaming stays off, is on **Production minify**.
"""
    ),
  )
end Overview
