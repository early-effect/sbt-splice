# sbt-splice

sbt-splice turns a Scala.js import specifier into pinned bytes and one script tag.

Docs: <https://www.earlyeffect.rocks/sbt-splice/>

Scala.js emits `import "preact"` for `@JSImport("preact")` (or `require`). A browser cannot resolve that **specifier**:
there is no `node_modules`. **sbt-splice** maps each specifier to pinned bytes (a file you copied, a WebJar, or a
CDN/GitHub download with sha256) and writes one script for a `<script>` tag.

You do not install Node, Vite, or esbuild. The plugin does not run Node either. `spliceFast` bundles the linker's
imports (development). `spliceFull` bundles and minifies that file (production): locals and whitespace shrink, JS
property names and `class` / `super` stay. The first build that needs esbuild fetches a pinned binary for this
OS/arch through Coursier. `spliceClosure` is optional Closure advanced on the Scala.js output. With no npm imports,
leave `spliceLibs` empty; that file stays a classic script, and `spliceFull` is still the production file.

Coordinate: `rocks.earlyeffect` % `sbt-splice`

```scala
// project/plugins.sbt
addSbtPlugin("rocks.earlyeffect" % "sbt-splice" % "0.3.2")
```

Enable Scala.js, map one library, run `spliceFast`:

```scala
enablePlugins(ScalaJSPlugin)
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }
spliceResolvers += Splice.jsDelivr
spliceLibs += Splice.lib("preact", "10.26.4", "dist/preact.module.js").sha256("…")
```

Output defaults to `target/splice/fast.js`, `target/splice/full.js`, and `target/splice/closure.js`. An unmapped
specifier fails the task and names the file. CDN and GitHub pins require sha256; a mismatch fails the task. A WebJar
uses project `resolvers`. There is no npm and no `package.json`. 0.3.2 is the published release.

## License

Apache-2.0
