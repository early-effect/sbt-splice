package rocks.earlyeffect.splice.docs

import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

object Production extends DocSpecSuite:

  def doc = page("Production minify")(
    md"""
The point of `spliceFull` is a modern production JS file **without a JS toolchain**. You do not install Node, Vite,
or esbuild. JDK and sbt were already on the machine. First `spliceFull` fetches a pinned esbuild for this OS/arch
through Coursier (sha256 of the binary, then `chmod +x`). After that it is a cache hit, like a jar.

`spliceFast` is concat (development). `spliceFull` is Scala.js minify of the Scala graph, then esbuild minify of the
printed script (vendor wrappers plus that output). Locals and whitespace shrink. JS **property** names stay, so a
Preact `class extends Component` still has `.render` / `.setState`. `class extends Error` keeps `super()`. That is
the Vite-shaped pass. It runs even when `spliceLibs` is empty.

`spliceClosure` is optional Closure advanced on the same printed file. JDK 21+ for that task only. Use it if you
want unused-vendor DCE that esbuild will not do on a wrap-IIFE.
""",
    section("Three tools, three jobs")(
      md"""
**Scala.js minify** (1.16+, on in `fullLink`) shortens **Scala** class fields and methods. The linker has types, so
those names cannot be a JS protocol (`render`, `setState`, `connectedCallback`). That is the type-aware property pass.

**esbuild** (pinned native binary, fetched on first `spliceFull`) then minifies the **printed** spliced file:
locals, syntax, whitespace. JS **property** names stay. `class extends Error` keeps `super()`. This is the pass
Scala.js 1.21 named Vite for, without Node.

**Closure** is the same printed file under `ADVANCED`, property renaming off. It can still DCE unused vendor exports
inside a wrap-IIFE. That is extra size, not the production default. JDK 21+.

The plugin never runs npm, never reads `package.json`, and never leaves `import "preact"` for a bundler to fix.
Sealed pins and one `<script>` tag are the product.
"""
    ),
    section("What Vite actually minifies")(
      md"""
Vite production minify shortens local variables, strips whitespace, and tree-shakes unused ESM exports. **Property
names stay.** `.setState`, `.render`, and `componentDidMount` are still those strings. `spliceFull` does the first
two with the same esbuild. It does not walk `node_modules` as ESM; spliced libraries are wrap-IIFEs, so unused
vendor exports stay unless you run `spliceClosure`.

Terser documents `mangle.properties` as unsafe and **off by default**. A Preact class component that worked under
Vite is the baseline, not a special case splice has to list names for.
"""
    ),
    section("What Closure advanced does that they refuse")(
      md"""
Closure `ADVANCED` can rename properties across the whole program. Google’s interoperability story is **externs for
every public name**: a whitelist. Mainstream bundlers never took that pass, so they never had to maintain one.

Scala.js emits a subclass of a spliced class as `class extends $$superClass`. Closure cannot see that the super is the
library. Property renaming and per-type disambiguation then split the protocol:

- The library’s `.render` is renamed, the override stays literal, and class components render nothing.
- Or `Component.prototype.setState` is kept, the subclass call becomes `a.uJ`, and you get `is not a function`.

Those are the same compiler mistake. `spliceFull` never takes that pass. `spliceClosure` turns property renaming,
disambiguation, and closed-world property analysis **off** for the whole compile. Every `.foo` stays `.foo`.
`h.render()` and `if (h.componentWillMount) h.componentWillMount()` stay dynamic, so a lazy Scala.js subclass
matches the spliced base. Consumers do not list `render` / `setState` / `connectedCallback`. There is no keep-list API.

Subclassing a spliced class needs no extra setting.
"""
    ),
    section("What we still want from Closure")(
      md"""
`spliceFull` (esbuild) is smaller than concatenating unminified `fullLink` with the same vendor files. Linker DCE
already dropped unused Scala. esbuild then shortens what is left. It does not unique-fold unused exports out of a
wrap-IIFE the way Closure can.

`spliceClosure` still does that vendor DCE, plus inlining, with property renaming off. It is not as small as Gmail-era
Closure with property renaming. Protocol strings stay.

Scala.js minify already emits `class $$c_jl_Throwable extends Error` with `super()`, then getter-only
`@JSExport("message")` / `@JSExport("name")`. That is SuperCall. esbuild `--target=es2015` keeps it. Closure
`languageOut` is ES2015 so `spliceClosure` does not rewrite it to `Error.call(this); this.message = …`.

`.extern` on a `spliceLibs` entry is a different hatch: skip advanced mode on a whole chunk Closure cannot compile.
`spliceFast` / `spliceFull` / `spliceClosure` still wrap and prepend that library. It is not how class components work.
"""
    ),
    section("Fast vs full")(
      exampleValue {
        List(
          "spliceFast"    -> "private remapped link, concat",
          "spliceFull"    -> "full-opt link + esbuild minify",
          "spliceClosure" -> "full-opt link + Closure (no JS property renaming)",
        )
      }.assert { pairs =>
        assertTrue(
          pairs.head._1 == "spliceFast",
          pairs(1)._1 == "spliceFull",
          pairs.last._1 == "spliceClosure",
        )
      }
    ),
  )
end Production
