package rocks.earlyeffect.splice.docs

import earlyeffect.docs.EarlyEffectTheme
import specular.site.*
import zio.*

import java.nio.file.Path

/** Docs-as-tests site builder (Test classpath; `docs/specularSite`). */
object BuildSite extends DocsSite:

  def pages = Vector(Overview.doc, Usage.doc, Production.doc, KeepingPins.doc)

  override def site: SiteModel =
    val m = meta
    super.site.copy(
      summaryMarkdown = Some(
        s"""**sbt-splice** writes one browser-loadable script from a Scala.js project. No Node, no Vite,
no `package.json`. Map `@JSImport` specifiers to pinned bytes, or leave `spliceLibs` empty.
`spliceFull` is production minify (pinned esbuild, fetched on first use). `spliceFast` is concat.
sbt 2 and Scala 3 only. Optional `spliceClosure` needs JDK 21+.
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
