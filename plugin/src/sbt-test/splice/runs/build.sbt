ThisBuild / scalaVersion := "3.9.0"

lazy val checkRuns = taskKey[Unit]("Eval spliceFast and spliceFull of @JSImport(preact) on GraalJS")

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .aggregate()
  .settings(
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    spliceLibs += Splice.file(
      "preact",
      baseDirectory.value / "vendor" / "preact@10.26.4.module.js",
    ),
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
    },
  )

lazy val checkShake = taskKey[Unit]("Unused ESM export dropped on fast and full, kept on closure")

lazy val shake = project
  .in(file("shake"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    Compile / scalaSource := baseDirectory.value / "src" / "main" / "scala",
    spliceLibs += Splice.file(
      "live",
      (LocalRootProject / baseDirectory).value / "vendor" / "shake" / "live.js",
    ),
    checkShake := {
      def check(label: String, f: File, markerStays: Boolean): Unit = {
        val body = IO.read(f)
        val has  = body.contains("DEAD_EXPORT_MARKER")
        if (markerStays && !has)
          sys.error(label + ": expected the unused export to stay in " + f)
        if (!markerStays && has)
          sys.error(label + ": unused export was not dropped in " + f)
        val got = JsHost.evalExpr(body, "document.getElementById('out').textContent")
        if (got != "one")
          sys.error(label + ": expected one, got " + got + " from " + f)
        ()
      }
      check("spliceFast", spliceFast.value, false)
      check("spliceFull", spliceFull.value, false)
      check("spliceClosure", spliceClosure.value, true)
    },
  )
