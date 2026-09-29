package rocks.earlyeffect.splice.docs

import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

object Production extends DocSpecSuite:

  def doc = page("Production minify")(
    md"""
The point of `spliceFull` is a modern production JS file **without a JS toolchain**. You do not install Node, Vite,
or esbuild. JDK and sbt were already on the machine. The first build that needs it fetches a pinned esbuild for this
OS/arch through Coursier (sha256 of the binary, then `chmod +x`). After that it is a cache hit, like a jar.

`spliceFast` and `spliceFull` leave the linker's imports in place. esbuild bundles that file into one classic script
(`--format=iife`, no global name) and resolves each mapped specifier to its pinned file. `spliceFast` is development
(`process.env.NODE_ENV` is `"development"`). `spliceFull` is the same invocation plus minify (`NODE_ENV`
`"production"`, `--target=es2015`). Locals and whitespace shrink. JS **property** names stay, so a Preact
`class extends Component` still has `.render` / `.setState`. `class extends Error` keeps `super()`. There is no
keep-list. Empty `spliceLibs` does not take this path: the file stays a classic script, and full still minifies it.

`spliceClosure` still rewrites mapped `@JSImport`s to `__splice_*` globals before link, still puts an
`export * as` library bundle ahead of Scala.js, and still does not parse library source. Unused library exports stay
there, so `spliceFull` is the one that drops them. JDK 21+ for that task only. Property renaming stays off. `process`
is a stub so ZIO-shaped code runs in a browser.
""",
    section("Three tools, three jobs")(
      md"""
**Scala.js minify** (1.16+, on in `fullLink`) shortens **Scala** class fields and methods. The linker has types, so
those names cannot be a JS protocol (`render`, `setState`, `connectedCallback`). That is the type-aware property pass.

**esbuild** (pinned native binary, fetched the first time a build needs it) bundles the linker's imports. For
`spliceFull` that same call minifies: locals, syntax, whitespace. JS **property** names stay. `class extends Error`
keeps `super()`. This is the pass Scala.js 1.21 named Vite for, without Node.

**Closure** runs `ADVANCED` on Scala.js's output alone, property renaming off, with the library bundle ahead of it and
its `__splice_*` bindings as externs. npm code was never written for Closure, so Closure never sees library source.
Unused library exports stay in that prefix. That is extra inlining for the Scala side, not the production default.
JDK 21+.

The plugin never runs npm, never reads `package.json`, and never leaves `import "preact"` for a bundler to fix.
Sealed pins and one `<script>` tag are the product.
"""
    ),
    section("What Vite actually minifies")(
      md"""
Vite production minify shortens local variables, strips whitespace, and drops unused ESM exports. **Property names
stay.** `.setState`, `.render`, and `connectedCallback` are still those strings. `spliceFull` does the same with
esbuild. A namespace import keeps only the properties the program reads. Passing the module as a value keeps the
rest. Top-level side effects stay. `export * as` inside a pinned file is not shaken. CommonJS is not promised to
shrink. A library the linker never imports is left out.

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
already dropped unused Scala. esbuild then drops unused ESM exports and shortens what is left. On that library axis
`spliceClosure` is the fatter file: its prefix keeps every export of a mapped module.

`spliceClosure` adds Closure's dead-code removal and inlining on the Scala side, with property renaming off. It is
not as small as Gmail-era Closure with property renaming. Protocol strings stay. Unused library exports stay too.

Scala.js minify already emits `class $$c_jl_Throwable extends Error` with `super()`, then getter-only
`@JSExport("message")` / `@JSExport("name")`. That is SuperCall. esbuild `--target=es2015` keeps it. Closure
`languageOut` is ES2015 so `spliceClosure` does not rewrite it to `Error.call(this); this.message = …`.
"""
    ),
    section("Fast vs full")(
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
      }
    ),
  )
end Production
