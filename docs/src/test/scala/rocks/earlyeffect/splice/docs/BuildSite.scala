package rocks.earlyeffect.splice.docs

import earlyeffect.docs.EarlyEffectTheme
import specular.site.*
import zio.*

import java.nio.file.Path

/** Docs-as-tests site builder (Test classpath; `docs/specularSite`). */
object BuildSite extends DocsSite:

  def pages = Vector(Overview.doc, Usage.doc)

  override def site: SiteModel =
    val m = meta
    super.site.copy(
      summaryMarkdown = Some(
        s"""**sbt-splice** takes Scala.js linker output and produces browser-loadable JavaScript
with bare module specifiers resolved. Zero Node: JS libraries arrive as pinned bytes
(vendor file, Maven/WebJar, or CDN fetch). sbt 2 and Scala 3 only.
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
