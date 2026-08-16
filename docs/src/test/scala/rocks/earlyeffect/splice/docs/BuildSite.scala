package rocks.earlyeffect.splice.docs

import earlyeffect.docs.EarlyEffectTheme
import specular.site.*
import zio.*

import java.nio.file.Path

/** Docs-as-tests site builder (Test classpath; `docs/specularSite`). */
object BuildSite extends DocsSite:

  def pages = Vector(Overview.doc, Usage.doc, KeepingPins.doc)

  override def site: SiteModel =
    val m = meta
    super.site.copy(
      summaryMarkdown = Some(
        s"""**sbt-splice** turns `@JSImport("preact")` into one browser-loadable script. Scala.js
still emits `import "preact"`; a browser cannot resolve that specifier. You map each name
to pinned bytes (a file, a WebJar, or a CDN/GitHub download with sha256). Then `spliceFast`
or `spliceFull` writes a file for a `<script>` tag. sbt 2 and Scala 3 only.
"""
      ),
      installSnippets = Vector(
        ArtifactKind.defaultInstall(m, ArtifactKind.Plugin)
      ),
      logo = Some(EarlyEffectTheme.logoHref),
      logoLink = Some("https://www.earlyeffect.rocks/"),
    )
  end site

  override def layers: ZLayer[Any, Nothing, SiteBuilder] =
    EarlyEffectTheme.layers

  override def afterBuild(out: Path, result: SiteOutput): Task[Unit] =
    EarlyEffectTheme.writeLogo(out)
end BuildSite
