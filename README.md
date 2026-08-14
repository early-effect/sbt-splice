# sbt-splice

sbt 2 / Scala 3 plugin: remap `@JSImport` in IR, private-link, wrap pinned JS (vendor file, Maven/WebJar, or CDN fetch) onto `globalThis.__splice_*`, emit browser-loadable JavaScript. Zero Node.

Coordinate: `rocks.earlyeffect` % `sbt-splice`

```scala
// project/plugins.sbt
addSbtPlugin("rocks.earlyeffect" % "sbt-splice" % "<version>")
```

Map a bare specifier to a vendored file, WebJar, or pinned CDN file, then run
`spliceFast` or `spliceFull`:

```scala
enablePlugins(ScalaJSPlugin)
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }
spliceResolvers += Splice.jsDelivr
spliceLibs += Splice.file("foo", baseDirectory.value / "vendor" / "foo.js")
spliceLibs += Splice.lib("preact", "10.26.4", "dist/preact.module.js").sha256("…")
```

Output defaults to `target/splice/fast.js` and `target/splice/full.js`. Unresolved
specifiers fail the task. CDN fetches require sha256; Maven/WebJar uses project
`resolvers`. `spliceFull` runs Closure advanced on the spliced file (one script).
`spliceFast` writes a source map by default; `spliceFull` does not. Tests in this
repo execute that output on GraalJS (JVM, not published).

The plugin is not released yet. Design and phases: [ROADMAP.md](ROADMAP.md).

## License

Apache-2.0
