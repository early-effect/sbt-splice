import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("escape-string-regexp", JSImport.Default)
object EscapeStringRegexp extends js.Object:
  def apply(string: String): String = js.native

object Hello:
  def main(args: Array[String]): Unit =
    val _ = EscapeStringRegexp("hello?")
