scalaVersion := "3.9.0"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true

lazy val es = project
  .in(file("es"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaVersion                    := "3.9.0",
    scalaJSUseMainModuleInitializer := true,
    Compile / scalaSource           := (LocalRootProject / baseDirectory).value / "src" / "main" / "scala",
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
  )
