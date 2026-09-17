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
`target/splice/full.js`. `spliceFull` is the Node-free production bundle (esbuild minify). It stubs
Node-shaped free-vars (starting with `process`) so isomorphic Scala.js such as ZIO `System.env` /
exit works without Node.

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

CDN and GitHub fetches go through Coursier (the same cache sbt uses for jars). The plugin never runs npm.
"""
    ),
    section("Tasks")(
      md"""
- `spliceFast` writes the development file (`target/splice/fast.js` by default). Source maps are on
  (`spliceFast / spliceSourceMaps`). Concat only.
- `spliceFull` writes the production file (`target/splice/full.js`), then minifies with a pinned native esbuild for
  this OS/arch (Coursier fetch, sha256, no user install). Source maps are off by default. This is the production
  task. It runs even when `spliceLibs` is empty.
- `spliceClosure` is optional Closure advanced (`target/splice/closure.js`). Use it when you want unused-vendor DCE
  that minify will not do. Closure needs **JDK 21+**.

`.extern` on a `spliceLibs` entry is a Closure hatch: `spliceFast` / `spliceFull` still include the library;
`spliceClosure` does not feed that chunk to advanced mode. Vendor files may be ESM, CJS, or UMD. AMD-only
`define()`, `export * from`, and `import.meta` fail the task.
"""
    ),
    section("Subclassing a spliced class")(
      md"""
`spliceFull` minifies like Vite: locals and whitespace, not JS property names. A Scala.js subclass of a spliced
class is `class extends $$superClass`. `render` / `setState` / `connectedCallback` stay those strings. `spliceClosure`
uses the same property policy (renaming off) plus Closure DCE. You do not declare overridable methods. The why is on
**Production minify**.

`.extern` is only for a library Closure cannot compile. It is not required for class components.
"""
    ),
  )
end Usage
