import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("foo", JSImport.Namespace)
object Foo extends js.Object:
  def greet(): String = js.native

object Hello:
  def main(args: Array[String]): Unit =
    val _ = Foo.greet()
