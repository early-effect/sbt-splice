package rocks.earlyeffect.splice.docs

import earlyeffect.docs.EarlyEffectTheme
import specular.DocPage
import specular.site.*
import zio.*

import java.nio.file.{Files, Path, StandardCopyOption}

/** Specular DocsSite: Test classpath main invoked by `docs/specularSite`. */
object BuildSite extends DocsSite:

  /** Not a nav item. The sidebar is the reference, not a second copy of this page. */
  private val frontPage: DocPage = Front.doc

  private val reference: Vector[DocPage] =
    Vector(Overview.doc, Usage.doc, Production.doc, KeepingPins.doc)

  def pages: Vector[DocPage] = frontPage +: reference

  override def site(settings: DocsSettings): SiteModel =
    EarlyEffectTheme
      .brand(super.site(settings))
      .copy(
        nav = Some(NavModel.flat(reference)),
        summaryMarkdown = None,
      )

  override def layers: ZLayer[Any, Nothing, SiteBuilder] =
    EarlyEffectTheme.layers

  override def afterBuild(out: Path, result: SiteOutput): IO[SiteError, Unit] =
    val _ = result
    // Specular always writes index.html as a summary plus a second copy of the nav.
    // The front DocPage is the index. Copy it over that file. Asset paths stay site-relative.
    val front   = out.resolve(s"${frontPage.slug}.html")
    val index   = out.resolve("index.html")
    val promote =
      ZIO
        .attemptBlockingIO(Files.copy(front, index, StandardCopyOption.REPLACE_EXISTING))
        .mapError { err =>
          SiteError.WriteFailed(index, err)
        }
    promote *> EarlyEffectTheme.writeLogo(out)
end BuildSite
