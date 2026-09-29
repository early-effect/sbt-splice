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
`spliceFast` bundles the linker's own imports and stops there (development, `NODE_ENV` is `"development"`).
`spliceFull` is that same bundle, minified (`NODE_ENV` is `"production"`). Source maps are on for fast and off for
full and closure unless you turn them on. Empty `spliceLibs` stays a classic script, not an iife, and `spliceFull`
still minifies it. `spliceClosure` is optional Closure advanced on Scala.js's output; unused library exports stay on
that path. Vanilla `fastLinkJS` / `fullLinkJS` are unchanged. Splice writes `target/splice/fast.js`,
`target/splice/full.js`, and `target/splice/closure.js`.
""",
      exampleValue {
        List(
          "spliceFast"    -> "bundle the linker's imports (development)",
          "spliceFull"    -> "bundle and minify (production)",
          "spliceClosure" -> "Closure on Scala.js; libraries are not shaken",
        )
      }.assert { pairs =>
        assertTrue(
          pairs match
            case ("spliceFast", _) :: ("spliceFull", _) :: ("spliceClosure", _) :: Nil => true
            case _                                                                     => false
        )
      },
    ),
    section("If you already use Vite")(
      md"""
You can drop Node from the Scala.js production path. Vite's minify leaves JS property names alone and keeps
`class` / `super()`. So does `spliceFull`. esbuild also drops the ESM exports the linked program does not use,
including an `import * as` when the program only reads some properties. Passing that module on as a value keeps
them. Top-level side effects stay. An `export * as` inside a pinned file is not shaken (esbuild's limit). CommonJS
is not promised to shrink. A library the linker never imports is absent. Splice does not walk `node_modules`, does
not read `package.json`, does not run npm, and does not leave `import "preact"` for another bundler to fix.
Libraries are sealed pins. The output is one `<script>` file.

How that compares to Closure, and why property renaming stays off, is on **Production minify**.
"""
    ),
  )
end Overview
