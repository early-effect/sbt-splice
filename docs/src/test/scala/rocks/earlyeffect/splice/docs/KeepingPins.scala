package rocks.earlyeffect.splice.docs

import specular.*
import specular.ziotest.DocSpecSuite

object KeepingPins extends DocSpecSuite:

  def doc = page("Keeping library pins current")(
    md"""
Skip this page unless you already use zipx, or you want GitHub to tell you when a pinned JS library has a security
advisory.

sbt-splice pins CDN and GitHub downloads by version plus sha256. Those pins are not Maven libraries, so Scala Steward
does not bump them. **sbt-splice-zipx** is an opt-in plugin that registers a zipx [pin feed](https://www.earlyeffect.rocks/zipx/)
named `splice` from your `spliceLibs`. sbt-splice itself does not depend on zipx.
""",
    section("Add the opt-in plugin")(
      md"""
```scala
// project/plugins.sbt
addSbtPlugin("rocks.earlyeffect" % "sbt-splice-zipx" % "<version>")
```

That artifact depends on sbt-splice and sbt-zipx. Do not put `Plugin("rocks.earlyeffect", "sbt-splice", …)` (or
`sbt-splice-zipx`) in your `ZipxVersions` catalog. Generate writes those lines from the jars on the classpath.

The feed is alert-only by default: outdated versions are ignored, advisories are reported, and nothing is submitted
to the GitHub Security tab. Locally, `zipxPinUpdate` still lists CDN and GitHub bumps and, with `yes`, rewrites the
version **and** sha256 together in `*.sbt` / `*.scala`. WebJar entries are advisory-only (no rewrite). Vendor files
are skipped.

Snapshot submit to the Security tab is off until you set `submitSnapshot = true` on the feed. Topology, PR gates, and
companion workflows live in zipx's Pin feeds page; this plugin does not retell that YAML.
"""
    ),
  )
end KeepingPins
