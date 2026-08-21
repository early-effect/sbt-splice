import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("widget", "Component")
class Component extends js.Object:
  def setState(state: js.Any): Unit = js.native
  def render(): String              = js.native
  def componentWillMount(): Unit    = js.native
  def componentDidMount(): String   = js.native

@js.native
@JSImport("widget", JSImport.Namespace)
object Widget extends js.Object:
  def mount(ctor: js.Dynamic): String = js.native

class HelloComponent extends Component:
  override def componentWillMount(): Unit =
    setState(js.Dictionary("v" -> "from-will-mount"))
  override def render(): String            = "OVERRIDE_RENDER"
  override def componentDidMount(): String = "OVERRIDE_DID_MOUNT"

object Hello:
  def main(args: Array[String]): Unit =
    val mounted = Widget.mount(js.constructorOf[HelloComponent])
    js.Dynamic.global.document.getElementById("out").textContent = mounted
