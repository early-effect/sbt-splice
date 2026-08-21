scalaVersion := "3.8.4"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }

spliceLibs += Splice.file(
  "preact",
  baseDirectory.value / "vendor" / "preact@10.26.4.module.js",
)

lazy val checkRuns = taskKey[Unit]("Eval spliceFast and spliceFull of @JSImport(preact) on GraalJS")

checkRuns := {
  def check(label: String, f: File): Unit = {
    val body = IO.read(f)
    if (body.contains("from \"preact\"") || body.contains("require(\"preact\")"))
      sys.error(label + ": leftover preact specifier in " + f)
    val got = JsHost.evalExpr(body, "document.getElementById('out').textContent")
    if (got != "h1:OVERRIDE_RENDER")
      sys.error(label + ": expected vnode type plus class render, got " + got + " from " + f)
    ()
  }
  check("spliceFast", spliceFast.value)
  check("spliceFull", spliceFull.value)
}
