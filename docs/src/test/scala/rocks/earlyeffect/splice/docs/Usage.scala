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
- `spliceFast` — depends on `Compile / fastLinkJS`, then splices. Phase 0 fails
  with "not implemented" after the link.
- `spliceFull` — depends on `Compile / fullLinkJS`, then splices and (later)
  Closure-optimizes the combined file.

Specifier maps, resolvers, and emit paths land in later phases. Until then the
tasks exist so builds and scripted tests can wire them.
"""
    ),
  )
end Usage
