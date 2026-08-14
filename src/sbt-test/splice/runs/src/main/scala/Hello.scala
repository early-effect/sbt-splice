import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("preact", JSImport.Namespace)
object Preact extends js.Object:
  def h(`type`: String, props: js.Any, children: js.Any*): js.Dynamic = js.native

object Hello:
  def main(args: Array[String]): Unit =
    val vnode = Preact.h("h1", js.undefined, "ok")
    js.Dynamic.global.document.getElementById("out").textContent = vnode.selectDynamic("type")
