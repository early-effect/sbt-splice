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
- `spliceFast` private-links `@JSImport` specifiers in `spliceLibs` to
  `globalThis.__splice_*`, then splices mapped files into `spliceFastOutput`
  (default `target/splice/fast.js`). Vanilla `fastLinkJS` is not rewritten.
  Source maps are on by default (`spliceFast / spliceSourceMaps`); the map is
  indexed, with a line offset equal to the prepended wrapper line count
  (including blanks).
- `spliceFull` does the same with the full-opt linker, then runs Closure
  advanced. The default artifact is one script (`target/splice/full.js`).
  Source maps are off by default; set `spliceFull / spliceSourceMaps := true`
  to ask Closure for a map.

Map bare specifiers to vendored files, WebJars, pinned CDN coordinates, or a
GitHub tag tarball:

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

CDN and GitHub coordinates require `sha256`. Maven/WebJar uses the project's
`resolvers` (a dedicated `splice` configuration, not the Compile classpath).
Add `Splice.jsDelivr` or `Splice.unpkg` to opt into CDNs, and `Splice.github`
to fetch `https://github.com/{owner}/{repo}/archive/refs/tags/{tag}.tar.gz`
(a 404 retries the `v`-prefixed tag). The pin is the tarball; splice extracts
one path after stripping GitHub's root directory. Unresolved specifiers fail
the task and name the specifier and the referring file.

Vendor files may be ESM, CJS, or UMD; splice wraps them so `@JSImport` sees a
namespace. AMD-only `define()`, `export * from`, and `import.meta` fail the task.
`.extern` on a `spliceLibs` entry is a Closure hatch: `spliceFast` still wraps
and prepends the library; `spliceFull` does not feed that chunk to advanced
mode.

`spliceFast` is readable spliced JS (one file today). `spliceFull` is one
Closure-advanced script. The plugin writes files; it does not live-reload and
it does not ship a JS engine. A preview server (ascent, Specular `DocsServe`)
serves the tree and reloads the tab. Do not copy Scala.js `SmallModulesFor`
onto `spliceFast` until it emits a directory of linker chunks; concatenating
those chunks is invalid ESM. This repo's tests execute the spliced file on
GraalJS (JVM, test classpath only) to prove a real `@JSImport` runs with no
Node.
"""
    ),
  )
end Usage
