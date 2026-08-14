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
- `spliceFast` — depends on `Compile / fastLinkJS`, then splices mapped files into
  `spliceFastOutput` (default `target/splice/fast.js`).
- `spliceFull` — depends on `Compile / fullLinkJS` and splices the same way.
  Closure on the combined file is Phase 3.

Map bare specifiers to vendored files:

```scala
spliceLibs += Splice.file("foo", baseDirectory.value / "vendor" / "foo.js")
```

Unresolved specifiers fail the task and name the specifier and the referring file.
"""
    ),
  )
end Usage
