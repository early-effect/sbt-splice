import java.nio.charset.StandardCharsets

import scala.scalajs.js

object Hello:
  def main(args: Array[String]): Unit =
    val cs = StandardCharsets.ISO_8859_1
    js.Dynamic.global.onmessage = ((_: js.Any) => ())
    val _ = cs.name()
