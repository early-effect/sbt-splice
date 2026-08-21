scalaVersion := "3.8.4"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }

spliceLibs += Splice.file("widget", baseDirectory.value / "vendor" / "widget.js")

lazy val checkClass =
  taskKey[Unit]("Eval spliceFast and spliceFull of a Scala.js class-component override and setState")

checkClass := Def.uncached {
  val expected                            = """STATE:{"v":"from-will-mount"}|OVERRIDE_RENDER|OVERRIDE_DID_MOUNT"""
  def check(label: String, f: File): Unit = {
    val body = IO.read(f)
    val got  = JsHost.evalExpr(body, "document.getElementById('out').textContent")
    if (got != expected)
      sys.error(label + ": expected " + expected + ", got " + got + " from " + f)
    ()
  }
  check("spliceFast", spliceFast.value)
  check("spliceFull", spliceFull.value)
}
