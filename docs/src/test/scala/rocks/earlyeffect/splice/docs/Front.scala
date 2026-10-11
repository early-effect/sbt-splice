package rocks.earlyeffect.splice.docs

import ascent.ast.{Attr, UI}
import ascent.domtypes.AttrValue
import mermoid.{LayoutConfig, Mermaid, RenderConfig, SvgNode, SvgRenderer}
import rocks.earlyeffect.splice.SplicePlugin
import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

/** Install coordinate. Always the last published tag. A bare next version is not on Central. */
object DocsVersion:
  /** Last published tag (`v0.3.2`). Update this in the release commit. */
  val published = "0.3.2"

  /** Returns [[published]]. A bare next version, `-ci`, `+`, and `SNAPSHOT` are unpublished. */
  def advertise(raw: String): String =
    if raw.trim == published then published else published

  /** `specular.meta.version` is the raw build version. Absent in unit tests, so those use [[published]]. */
  def version: String =
    sys.props.get("specular.meta.version").map(advertise).getOrElse(published)
end DocsVersion

/** The site front. Specular still emits a summary index; [[BuildSite]] copies this page over it.
  *
  * Not a sidebar item. Overview, Usage, Production, and Keeping library pins current stay the reference.
  */
object Front extends DocSpecSuite:

  private val coordinate: String =
    s"""addSbtPlugin("rocks.earlyeffect" % "sbt-splice" % "${DocsVersion.version}")"""

  /** Narrow enough for a 390px column after the content padding. */
  private val figure = RenderConfig(layout = LayoutConfig(hSpacing = 16, padding = 8, vSpacing = 28))

  private val pipeline =
    Mermaid("""flowchart TB
              |    spec["import specifier"] --> link["fullLinkJS"]
              |    link --> pin["pinned bytes"]
              |    pin --> bundle["bundle and minify"]
              |    bundle --> tag["script tag"]
              |""".stripMargin)

  private def plainText(ui: UI[Any]): String = ui match
    case UI.Text(value)            => value
    case element: UI.Element[Any]  => element.children.map(plainText).mkString
    case UI.Empty                  => ""
    case UI.Fragment(children)     => children.map(plainText).mkString
    case UI.ReactiveText(_)        => ""
    case UI.ReactiveChild(_)       => ""
    case UI.When(_, _)             => ""
    case UI.ForEach(_, _, _)       => ""
    case UI.ForEachSignal(_, _, _) => ""
    case UI.Scoped(_)              => ""
    case UI.ServerRegion(_, _)     => ""

  private def svgWidths(ui: UI[Any]): List[Double] = ui match
    case element: UI.Element[Any] =>
      val here =
        if element.tag == "svg" then
          element.attrs.collect { case Attr.StaticAttr("width", AttrValue.Str(raw)) =>
            raw.toDoubleOption
          }.flatten
        else Nil
      here.toList ++ element.children.toList.flatMap(svgWidths)
    case UI.Fragment(children) => children.toList.flatMap(svgWidths)
    case UI.Text(_) | UI.Empty | UI.ReactiveText(_) | UI.ReactiveChild(_) | UI.When(_, _) | UI.ForEach(_, _, _) |
        UI.ForEachSignal(_, _, _) | UI.Scoped(_) | UI.ServerRegion(_, _) =>
      Nil

  private def diagram: UI[Any] =
    val tree = SvgRenderer.renderTree(pipeline.diagram, figure)
    toUi(fit(tree))

  /** Scale down inside a narrow column. The viewBox stays the painted size. */
  private def fit(node: SvgNode): SvgNode = node match
    case SvgNode.Element("svg", attrs, children) =>
      val rest = attrs.filterNot((name, _) => name == "style")
      SvgNode.Element("svg", rest :+ ("style" -> "max-width:100%;height:auto"), children)
    case other => other

  private def toUi(node: SvgNode): UI[Any] = node match
    case SvgNode.Text(value)                   => UI.Text(value)
    case SvgNode.Raw(content)                  => UI.Text(content)
    case SvgNode.Element(tag, attrs, children) =>
      UI.Element(
        tag,
        attrs.map((name, value) => Attr.StaticAttr(name, AttrValue.Str(value))).toVector,
        children.map(toUi).toVector,
      )

  def doc = page("One script tag")(
    md"""
sbt-splice turns a Scala.js import specifier into pinned bytes and one script tag.
""",
    section("What the browser loads")(
      md"""
`spliceFull` links, pins each specifier to bytes, then bundles and minifies that file. The browser loads one script.

```html
import "preact"
```

```html
<script src="full.js"></script>
```
""",
      illustration(diagram).assert { ui =>
        val text   = plainText(ui)
        val widths = svgWidths(ui)
        assertTrue(
          text.contains("import specifier"),
          text.contains("fullLinkJS"),
          text.contains("pinned bytes"),
          text.contains("bundle and minify"),
          text.contains("script tag"),
          widths.nonEmpty,
          widths.forall(_ > 0),
          widths.forall(_ <= 340),
        ).label(s"widths ${widths.mkString(",")}")
      },
    ),
    section("Install")(
      md"""
The coordinate is the last published release. A build whose version contains `-ci`, `+`, or `SNAPSHOT` still shows
that release.

```scala
${coordinate}
```
"""
    ),
    section("The task")(
      md"""
`spliceFull` is the val `SplicePlugin.autoImport.spliceFull`. It is not a method, so the cite is `cite(SplicePlugin.autoImport.spliceFull)`. The panel is that key. The footer is the definition.
""",
      cite(SplicePlugin.autoImport.spliceFull),
    ),
  )

  override def spec =
    suite("One script tag")(
      super.spec,
      test("install coordinate is the published release") {
        val version = DocsVersion.version
        assertTrue(
          version == DocsVersion.published,
          coordinate.contains(version),
          coordinate.contains("addSbtPlugin"),
          coordinate.contains("rocks.earlyeffect"),
          coordinate.contains("% \"sbt-splice\""),
          !coordinate.contains("%%"),
          !coordinate.contains("<version>"),
          !version.contains("-ci"),
          !version.contains("+"),
          !version.contains("SNAPSHOT"),
          !coordinate.contains("-ci"),
          !coordinate.contains("SNAPSHOT"),
          !coordinate.contains("0.3.3"),
          !coordinate.contains("0.4.0"),
          DocsVersion.advertise("0.3.3") == DocsVersion.published,
          DocsVersion.advertise("0.3.3-ci") == DocsVersion.published,
          DocsVersion.advertise("0.3.2+1-abc") == DocsVersion.published,
          DocsVersion.advertise(DocsVersion.published) == DocsVersion.published,
        )
      },
    )
end Front
