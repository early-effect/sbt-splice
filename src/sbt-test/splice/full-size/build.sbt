scalaVersion := "3.8.4"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }

spliceLibs += Splice.file(
  "escape-string-regexp",
  baseDirectory.value / "vendor" / "escape-string-regexp@5.0.0.js",
)

lazy val checkSize = taskKey[Unit]("Fail unless spliceFull is smaller than unminified concat")

checkSize := {
  val fullOut   = spliceFull.value
  val linkerDir = (Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value
  val vendor    = baseDirectory.value / "vendor" / "escape-string-regexp@5.0.0.js"
  val linkerLen = Option(linkerDir.listFiles).toList.flatten
    .filter(f => f.isFile && f.getName.endsWith(".js") && !f.getName.endsWith(".map"))
    .map(f => IO.readBytes(f).length)
    .sum
  val concat = linkerLen + IO.readBytes(vendor).length
  val got    = IO.readBytes(fullOut).length
  streams.value.log.info("spliceFull=" + got + " concat=" + concat)
  if (got >= concat) {
    sys.error("size budget: spliceFull=" + got + " not smaller than concat=" + concat)
  }
  val t1 = fullOut.lastModified
  Thread.sleep(1000)
  val _ = spliceFull.value
  if (fullOut.lastModified != t1) {
    sys.error("spliceFull cache: second run rewrote " + fullOut)
  }
  ()
}
