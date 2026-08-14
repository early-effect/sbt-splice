package rocks.earlyeffect.splice.docs

import specular.*
import specular.ziotest.DocSpecSuite

object Usage extends DocSpecSuite:

  def doc = page("Usage")(
    md"""
sbt-splice is published for **sbt 2** and **Scala 3** only (the `_sbt2_3`
coordinate). There is no sbt 1 artifact.
""",
    section("Install")(
      md"""
Add the plugin from Maven Central. It depends on sbt-scalajs transitively.

```scala
// project/plugins.sbt
addSbtPlugin("rocks.earlyeffect" % "sbt-splice" % "<version>")
```

Enable Scala.js on the project that links. Splice attaches itself via
`allRequirements` once `ScalaJSPlugin` is on the classpath.

```scala
enablePlugins(ScalaJSPlugin)
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }
```
"""
    ),
    section("Tasks")(
      md"""
- `spliceFast` depends on `Compile / fastLinkJS`, then splices mapped files into
  `spliceFastOutput` (default `target/splice/fast.js`).
- `spliceFull` depends on `Compile / fullLinkJS`, splices the same map, then
  runs Closure advanced on the combined file. The default artifact is one
  script (`target/splice/full.js`), not an ES module. Source maps are not
  produced on this path yet.

Map bare specifiers to vendored files, WebJars, or pinned CDN coordinates:

```scala
spliceResolvers += Splice.jsDelivr

spliceLibs += Splice.file("foo", baseDirectory.value / "vendor" / "foo.js")
spliceLibs += Splice.webjar("htm", "3.1.4", "dist/htm.module.js")
spliceLibs += Splice.lib("preact", "10.26.4", "dist/preact.module.js")
                 .sha256("…")
```

CDN coordinates require `sha256`. Maven/WebJar uses the project's `resolvers`
(a dedicated `splice` configuration, not the Compile classpath). Add
`Splice.jsDelivr` or `Splice.unpkg` to opt into CDNs. Unresolved specifiers fail
the task and name the specifier and the referring file.
"""
    ),
  )
end Usage
