scalaVersion := "3.9.0"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }

spliceLibs += Splice.file("widget", baseDirectory.value / "vendor" / "widget.js")

lazy val checkClass =
  taskKey[Unit]("Eval spliceFast, spliceFull, and spliceClosure of a Scala.js class-component override")

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
  check("spliceClosure", spliceClosure.value)
}
