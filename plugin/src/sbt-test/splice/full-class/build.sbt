scalaVersion := "3.8.4"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }

spliceLibs += Splice
  .file("widget", baseDirectory.value / "vendor" / "widget.js")
  .keep(Splice.classComponent*)

lazy val checkClass = taskKey[Unit]("Eval spliceFast and spliceFull of a Scala.js class-component override")

checkClass := {
  def check(label: String, f: File): Unit = {
    val body = IO.read(f)
    val got  = JsHost.evalExpr(body, "document.getElementById('out').textContent")
    if (got != "OVERRIDE_RENDER")
      sys.error(label + ": expected OVERRIDE_RENDER, got " + got + " from " + f)
    ()
  }
  check("spliceFast", spliceFast.value)
  check("spliceFull", spliceFull.value)
}
