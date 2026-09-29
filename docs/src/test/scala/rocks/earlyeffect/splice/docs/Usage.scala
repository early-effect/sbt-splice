package rocks.earlyeffect.splice.docs

import specular.*
import specular.ziotest.DocSpecSuite

object Usage extends DocSpecSuite:

  def doc = page("Usage")(
    md"""
sbt-splice is published for **sbt 2** and **Scala 3** only (the `_sbt2_3` coordinate). There is no sbt 1 artifact.
""",
    section("Install")(
      md"""
Add the plugin from Maven Central. It depends on sbt-scalajs transitively.

```scala
// project/plugins.sbt
addSbtPlugin("rocks.earlyeffect" % "sbt-splice" % "<version>")
```

Enable Scala.js on the project that links. Splice attaches itself via `allRequirements` once `ScalaJSPlugin` is on
the classpath. Use `ModuleKind.ESModule` (or CommonJS) when you have `@JSImport`. Skip that for a Scala-only app;
`NoModule` is the default.
"""
    ),
    section("Scala-only (no npm imports)")(
      md"""
Leave `spliceLibs` empty. `spliceFast` and `spliceFull` still write `target/splice/fast.js` and
`target/splice/full.js`. That file stays a classic script: it is not wrapped in an iife. You do not install Node,
and the plugin does not run Node either. `spliceFull` minifies with the pinned esbuild. `spliceClosure` stubs
`process` (`env`, `exitCode`, `browser`) so ZIO-shaped `System.env` / exit can run in a browser. When a mapped
library reads `process.env.NODE_ENV`, `spliceFast` sets `"development"` and `spliceFull` sets `"production"`.

```scala
enablePlugins(ScalaJSPlugin)
scalaJSUseMainModuleInitializer := true
```

Then in sbt: `spliceFull`. `NoModule` is the natural kind here.
"""
    ),
    section("Map one library and run spliceFast")(
      md"""
For `@JSImport`, set `ESModule` and tell splice which bytes the specifier `"preact"` is, then run `spliceFast`. The
default output is `target/splice/fast.js`. Put that file in a `<script>` tag (or copy it next to your HTML).

```scala
enablePlugins(ScalaJSPlugin)
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }
spliceResolvers += Splice.jsDelivr
spliceLibs += Splice.lib("preact", "10.26.4", "dist/preact.module.js").sha256("…")
```

Then in sbt: `spliceFast`. Unresolved specifiers fail the task and name the specifier.
"""
    ),
    section("Where the bytes come from")(
      md"""
Each `spliceLibs` entry is a specifier plus a source:

```scala
spliceResolvers += Splice.jsDelivr
spliceResolvers += Splice.github

spliceLibs += Splice.file("foo", baseDirectory.value / "vendor" / "foo.js")
spliceLibs += Splice.webjar("htm", "3.1.4", "dist/htm.module.js")
spliceLibs += Splice.lib("preact", "10.26.4", "dist/preact.module.js")
                 .sha256("…")
spliceLibs += Splice.github("foo", "owner/repo", "1.2.3", "dist/foo.js")
                 .sha256("…")
```

- **File:** a `.js` you copied into the repo. Git is the pin.
- **WebJar:** Maven publishes the npm file inside a jar (`org.webjars.npm`). Project `resolvers` and checksums pin it.
- **CDN pin:** package name, version, and path on jsDelivr or unpkg. **sha256** is required (a hex checksum of the
  downloaded file). Add `Splice.jsDelivr` or `Splice.unpkg` to `spliceResolvers`.
- **GitHub pin:** `owner/repo`, an exact tag, and a path inside that tag's tarball. **sha256** pins the tarball. Add
  `Splice.github`. A 404 retries the `v`-prefixed tag.

A sha256 mismatch fails the task. CDN and GitHub fetches go through Coursier (the same cache sbt uses for jars).
So does the pinned esbuild binary, the first time a build needs it. The plugin never runs npm, never reads
`package.json`, and never asks you to install esbuild.
"""
    ),
    section("Tasks")(
      md"""
- `spliceFast` writes `target/splice/fast.js`. It links, then esbuild bundles the linker's own imports
  (`NODE_ENV` `"development"`). Nothing is minified. Source maps are on (`spliceFast / spliceSourceMaps`).
- `spliceFull` writes `target/splice/full.js`. Same bundle, plus minify, in one esbuild run (`NODE_ENV`
  `"production"`). Locals and whitespace shrink. JS property names stay. Source maps are off unless you set
  `spliceFull / spliceSourceMaps`. This is the production task. Empty `spliceLibs` still minifies, and that output
  stays a classic script.
- `spliceClosure` writes `target/splice/closure.js`. It links with mapped imports rewritten to `__splice_*` globals,
  then Closure advanced on the Scala.js output only. The library prefix is not shaken, so unused library exports
  stay and this file can be larger than `spliceFull` on that axis. JDK 21+ for this task only. Property renaming
  stays off. Source maps are off unless you set `spliceClosure / spliceSourceMaps`.

The module initializer runs. An `export` from the Scala.js file is not copied onto `window`.

esbuild drops the ESM exports that import does not use, including `import * as` when the program only touches some
properties. Passing the module as a value keeps them. Top-level side effects stay. `export * as` inside a pinned
file is not shaken. CommonJS is not promised to shrink. A mapped library the linker never imports is absent.

Vendor files may be ESM, CommonJS, or UMD, and may import each other, re-export (`export * from` included), or import
for side effects. These fail the task: an unmapped specifier (the message names it and the file), a library that
reads `import.meta`, esbuild unable to bundle, Closure unable to parse the Scala.js output, and more than one linker
file on `spliceFast` or `spliceFull` when a library is mapped. Empty `spliceLibs` still concatenates. So does
`spliceClosure`.
"""
    ),
    section("Subclassing a spliced class")(
      md"""
`spliceFull` minifies like Vite: locals and whitespace, not JS property names. A Scala.js subclass of a spliced
class is `class extends $$superClass`. `render` / `setState` / `connectedCallback` stay those strings. You do not
declare overridable methods. `spliceClosure` uses the same property policy. The why is on **Production minify**.
"""
    ),
  )
end Usage
