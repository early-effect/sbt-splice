scalaVersion := "3.8.4"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }

spliceLibs += Splice.file("element", baseDirectory.value / "vendor" / "element.js")

lazy val checkElement = taskKey[Unit]("Eval spliceFast and spliceFull of a custom-element connectedCallback override")

checkElement := {
  def check(label: String, f: File): Unit = {
    val body = IO.read(f)
    val got  = JsHost.evalExpr(body, "document.getElementById('out').textContent")
    if (got != "OVERRIDE_CONNECTED")
      sys.error(label + ": expected OVERRIDE_CONNECTED, got " + got + " from " + f)
    ()
  }
  check("spliceFast", spliceFast.value)
  check("spliceFull", spliceFull.value)
}
