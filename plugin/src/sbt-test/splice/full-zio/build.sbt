scalaVersion := "3.8.4"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true

libraryDependencies ++= Seq(
  "dev.zio"           %% "zio"             % "2.1.26",
  "io.github.cquiroz" %% "scala-java-time" % "2.7.0",
)

lazy val es = project
  .in(file("es"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaVersion                    := "3.8.4",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "dev.zio"           %% "zio"             % "2.1.26",
      "io.github.cquiroz" %% "scala-java-time" % "2.7.0",
    ),
    Compile / scalaSource := (LocalRootProject / baseDirectory).value / "src" / "main" / "scala",
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
  )
