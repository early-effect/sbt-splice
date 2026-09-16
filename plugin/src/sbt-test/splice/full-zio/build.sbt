scalaVersion := "3.8.4"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true

libraryDependencies ++= Seq(
  "dev.zio"           %% "zio"             % "2.1.26",
  "io.github.cquiroz" %% "scala-java-time" % "2.7.0",
)

lazy val checkThrowable =
  taskKey[Unit]("spliceFull must keep class extends Error, not Error.call")

def checkThrowableSettings = Def.settings(
  checkThrowable := Def.uncached {
    val body = IO.read(spliceFull.value)
    if (body.contains("Error.call("))
      sys.error("spliceFull rewrote Error with Error.call in " + spliceFull.value)
    if (!body.contains("extends Error"))
      sys.error("spliceFull dropped class extends Error in " + spliceFull.value)
    ()
  }
)

checkThrowableSettings

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
  .settings(checkThrowableSettings)
