package rocks.earlyeffect.splice.docs

import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

object Production extends DocSpecSuite:

  def doc = page("Production minify")(
    md"""
`spliceFast` is the development file. `spliceFull` is the production file: Scala.js minify, then a JVM **Closure**
pass on the combined script (vendor wrappers plus that minify output). That pass runs even when `spliceLibs` is
empty. Closure needs **JDK 21+**.

This page is why that follow-up is Closure, why it is not Vite, and why it does not rename JS properties.
""",
    section("Three tools, three jobs")(
      md"""
**Scala.js minify** (1.16+, on in `fullLink`) shortens **Scala** class fields and methods. The linker has types, so
those names cannot be a JS protocol (`render`, `setState`, `connectedCallback`). That is the type-aware property pass.

**Closure** then sees the **printed** spliced file. Unused vendor exports can be dropped. Local variables can be
shortened. Functions can be inlined. JS **property** names stay as written, and Closure does not collapse or
devirtualize them into a single base implementation.

**Vite** is a Node bundler plus a minifier (Oxc today, esbuild before that, Terser if you opt in). Splice will not wrap
it. The plugin never runs npm, never reads `package.json`, and never leaves `import "preact"` for a bundler to fix.
Sealed pins and one `<script>` tag are the product. Scala.js 1.21 suggests following `fullLinkJS` with a JavaScript
minifier and names Vite / Rolldown; ours is JVM Closure because of that Node constraint.
"""
    ),
    section("What Vite actually minifies")(
      md"""
Vite production minify shortens local variables, strips whitespace, and tree-shakes unused ESM exports. **Property
names stay.** `.setState`, `.render`, and `componentDidMount` are still those strings.

Terser documents `mangle.properties` as unsafe and **off by default**. People who turn it on usually mangle only a
private regex (`/^_/`), not a catalog of framework methods. A Preact class component that worked under Vite is the
baseline, not a special case splice has to list names for.
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

Those are the same compiler mistake. The fix is to turn property renaming, disambiguation, and closed-world
property analysis **off** for the whole `spliceFull` compile. Every `.foo` stays `.foo`. `h.render()` and
`if (h.componentWillMount) h.componentWillMount()` stay dynamic, so a lazy Scala.js subclass matches the
spliced base. Consumers do not list `render` / `setState` / `connectedCallback`. There is no keep-list API.

Subclassing a spliced class needs no extra setting.
"""
    ),
    section("What we still want from Closure")(
      md"""
Dead-code elimination of unused vendor exports, inlining, and local-variable minify, on **one** compilation unit
(Scala.js output plus every spliced file). That is smaller than concatenating unminified `fullLink` with the same
vendor files. It is not as small as Gmail-era Closure with property renaming. Protocol strings stay; that is the
Vite-shaped trade.

`.extern` on a `spliceLibs` entry is a different hatch: skip advanced mode on a whole chunk Closure cannot compile.
Both tasks still wrap and prepend that library. It is not how class components work.
"""
    ),
    section("Fast vs full")(
      exampleValue {
        List(
          "spliceFast" -> "private remapped link, readable enough",
          "spliceFull" -> "full-opt link + Closure (no JS property renaming)",
        )
      }.assert { pairs =>
        assertTrue(
          pairs.head._1 == "spliceFast",
          pairs.last._1 == "spliceFull",
        )
      }
    ),
  )
end Production
