# sbt-splice

Scala.js emits `import "preact"` for `@JSImport("preact")` (or `require`). A browser cannot resolve that **specifier**:
there is no `node_modules`. **sbt-splice** is an sbt 2 / Scala 3 plugin that maps each specifier to pinned bytes (a
file you copied, a WebJar, or a CDN/GitHub download with sha256) and writes one script for a `<script>` tag. With no
npm imports, leave `spliceLibs` empty; `spliceFull` is still the production file. `spliceFast` is development;
`spliceFull` is production (Closure, JDK 21+). The plugin never runs npm.

Coordinate: `rocks.earlyeffect` % `sbt-splice`

```scala
// project/plugins.sbt
addSbtPlugin("rocks.earlyeffect" % "sbt-splice" % "<version>")
```

Enable Scala.js, map one library, run `spliceFast`:

```scala
enablePlugins(ScalaJSPlugin)
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }
spliceResolvers += Splice.jsDelivr
spliceLibs += Splice.lib("preact", "10.26.4", "dist/preact.module.js").sha256("…")
```

Output defaults to `target/splice/fast.js` and `target/splice/full.js`. Unresolved specifiers fail the task. CDN and
GitHub pins require sha256; a WebJar uses project `resolvers`. The plugin is not released yet. Design:
[ROADMAP.md](ROADMAP.md).

## License

Apache-2.0
