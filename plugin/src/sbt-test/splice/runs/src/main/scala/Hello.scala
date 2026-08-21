import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("preact", JSImport.Namespace)
object Preact extends js.Object:
  def h(`type`: String, props: js.Any, children: js.Any*): js.Dynamic = js.native

@js.native
@JSImport("preact", "Component")
class Component extends js.Object:
  def setState(state: js.Any): Unit                   = js.native
  def render(props: js.Any, state: js.Any): js.Any    = js.native
  def componentWillMount(): Unit                      = js.native

class HelloComponent extends Component:
  override def componentWillMount(): Unit =
    setState(js.Dictionary("v" -> 1))
  override def render(props: js.Any, state: js.Any): js.Any =
    "OVERRIDE_RENDER"

object Hello:
  def main(args: Array[String]): Unit =
    val vnode    = Preact.h("h1", js.undefined, "ok")
    val c        = new HelloComponent()
    c.componentWillMount()
    val rendered = c.render((), ())
    js.Dynamic.global.document.getElementById("out").textContent =
      vnode.selectDynamic("type").toString + ":" + rendered.toString
