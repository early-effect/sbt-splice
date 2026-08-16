scalaVersion := "3.8.4"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }

scalaVersion := "3.8.4"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }

lazy val webjarRepo = settingKey[File]("stub webjar maven repo")
webjarRepo := {
  val dir = baseDirectory.value / "repo"
  WebjarRepo.write(dir)
  dir
}
resolvers += "webjar-stub" at webjarRepo.value.toURI.toASCIIString

spliceLibs += Splice.webjar("foo", "1.0.0", "foo.js")

lazy val checkSpliced = taskKey[Unit]("Fail if spliceFast left a bare foo specifier")

checkSpliced := {
  val f = spliceFast.value
  val t = IO.read(f)
  if (t.contains("""from "foo"""") || t.contains("""require("foo")"""))
    sys.error(s"leftover foo specifier in $f")
  if (!t.contains("__splice_foo"))
    sys.error(s"expected wrapped foo module in $f")
}
