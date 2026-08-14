# sbt-splice

sbt 2 / Scala 3 plugin: take Scala.js linker output, resolve bare module specifiers against pinned JS (vendor file, Maven/WebJar, or CDN fetch), emit browser-loadable JavaScript. Zero Node.

Coordinate: `rocks.earlyeffect` % `sbt-splice`

```scala
// project/plugins.sbt
addSbtPlugin("rocks.earlyeffect" % "sbt-splice" % "<version>")
```

The plugin is not released yet. Design and phases: [ROADMAP.md](ROADMAP.md).

## License

Apache-2.0
