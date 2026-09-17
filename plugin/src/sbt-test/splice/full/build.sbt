ThisBuild / scalaVersion := "3.9.0"

lazy val checkClass   = taskKey[Unit]("class-component override and setState")
lazy val checkElement = taskKey[Unit]("custom-element connectedCallback override")
lazy val checkSize    = taskKey[Unit]("spliceFull smaller than unminified concat")
lazy val checkThrowable =
  taskKey[Unit]("spliceFull must keep class extends Error, not Error.call")

def jsApp = Def.settings(
  scalaJSUseMainModuleInitializer := true,
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
)

lazy val root = project.in(file(".")).aggregate()

lazy val widget = project
  .in(file("widget"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    jsApp,
    Compile / scalaSource := (LocalRootProject / baseDirectory).value / "src" / "widget",
    spliceLibs += Splice.file("widget", (LocalRootProject / baseDirectory).value / "vendor" / "widget.js"),
    checkClass := Def.uncached {
      val expected = """STATE:{"v":"from-will-mount"}|OVERRIDE_RENDER|OVERRIDE_DID_MOUNT"""
      def check(label: String, f: File): Unit =
        val got = JsHost.evalExpr(IO.read(f), "document.getElementById('out').textContent")
        if (got != expected)
          sys.error(label + ": expected " + expected + ", got " + got + " from " + f)
        ()
      check("spliceFast", spliceFast.value)
      check("spliceFull", spliceFull.value)
      check("spliceClosure", spliceClosure.value)
    },
  )

lazy val element = project
  .in(file("element"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    jsApp,
    Compile / scalaSource := (LocalRootProject / baseDirectory).value / "src" / "element",
    spliceLibs += Splice.file("element", (LocalRootProject / baseDirectory).value / "vendor" / "element.js"),
    checkElement := Def.uncached {
      def check(label: String, f: File): Unit =
        val got = JsHost.evalExpr(IO.read(f), "document.getElementById('out').textContent")
        if (got != "OVERRIDE_CONNECTED")
          sys.error(label + ": expected OVERRIDE_CONNECTED, got " + got + " from " + f)
        ()
      check("spliceFast", spliceFast.value)
      check("spliceFull", spliceFull.value)
    },
  )

lazy val size = project
  .in(file("size"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    jsApp,
    Compile / scalaSource := (LocalRootProject / baseDirectory).value / "src" / "size",
    spliceLibs += Splice.file(
      "escape-string-regexp",
      (LocalRootProject / baseDirectory).value / "vendor" / "escape-string-regexp@5.0.0.js",
    ),
    checkSize := Def.uncached {
      val fullOut   = spliceFull.value
      val linkerDir = baseDirectory.value / "target" / "splice" / "full-link"
      val vendor    = (LocalRootProject / baseDirectory).value / "vendor" / "escape-string-regexp@5.0.0.js"
      val linkerLen = Option(linkerDir.listFiles).toList.flatten
        .filter(f => f.isFile && f.getName.endsWith(".js") && !f.getName.endsWith(".map"))
        .map(f => IO.readBytes(f).length)
        .sum
      val concat = linkerLen + IO.readBytes(vendor).length
      val got    = IO.readBytes(fullOut).length
      streams.value.log.info("spliceFull=" + got + " concat=" + concat)
      if (got >= concat)
        sys.error("size budget: spliceFull=" + got + " not smaller than concat=" + concat)
      val map = new File(fullOut.getPath + ".map")
      if (map.exists)
        sys.error("spliceFull wrote a source map by default: " + map)
      val t1 = fullOut.lastModified
      Thread.sleep(1000)
      val _ = spliceFull.value
      if (fullOut.lastModified != t1)
        sys.error("spliceFull cache: second run rewrote " + fullOut)
      ()
    },
  )

def hostSettings = Def.settings(
  scalaJSUseMainModuleInitializer := true,
  Compile / scalaSource           := (LocalRootProject / baseDirectory).value / "src" / "host",
)

lazy val host = project
  .in(file("host"))
  .enablePlugins(ScalaJSPlugin)
  .settings(hostSettings)

lazy val hostEs = project
  .in(file("host-es"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    hostSettings,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
  )

def zioSettings = Def.settings(
  scalaJSUseMainModuleInitializer := true,
  Compile / scalaSource           := (LocalRootProject / baseDirectory).value / "src" / "zio",
  libraryDependencies ++= Seq(
    "dev.zio"           %% "zio"             % "2.1.26",
    "io.github.cquiroz" %% "scala-java-time" % "2.7.0",
  ),
  checkThrowable := Def.uncached {
    val body = IO.read(spliceFull.value)
    if (body.contains("Error.call("))
      sys.error("spliceFull rewrote Error with Error.call in " + spliceFull.value)
    if (!body.contains("extends Error"))
      sys.error("spliceFull dropped class extends Error in " + spliceFull.value)
    ()
  },
)

lazy val zio = project
  .in(file("zio"))
  .enablePlugins(ScalaJSPlugin)
  .settings(zioSettings)

lazy val zioEs = project
  .in(file("zio-es"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    zioSettings,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
  )
