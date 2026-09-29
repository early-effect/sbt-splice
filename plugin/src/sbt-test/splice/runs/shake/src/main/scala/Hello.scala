import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("live", JSImport.Namespace)
object Live extends js.Object:
  def one(): String = js.native

object Hello:
  def main(args: Array[String]): Unit =
    js.Dynamic.global.document.getElementById("out").textContent = Live.one()
