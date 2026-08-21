import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("element", "HtmlElement")
class HtmlElement extends js.Object:
  def connectedCallback(): String        = js.native
  def disconnectedCallback(): String     = js.native
  def attributeChangedCallback(): String = js.native

@js.native
@JSImport("element", JSImport.Namespace)
object ElementLib extends js.Object:
  def upgrade(ctor: js.Dynamic): String = js.native

class HelloElement extends HtmlElement:
  override def connectedCallback(): String = "OVERRIDE_CONNECTED"

object Hello:
  def main(args: Array[String]): Unit =
    val got = ElementLib.upgrade(js.constructorOf[HelloElement])
    js.Dynamic.global.document.getElementById("out").textContent = got
