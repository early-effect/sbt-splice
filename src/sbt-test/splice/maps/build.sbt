scalaVersion := "3.8.4"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }

spliceLibs += Splice.file("foo", baseDirectory.value / "vendor" / "foo.js")
spliceFastOutput := baseDirectory.value / "target" / "custom" / "app.js"

lazy val checkSpliced = taskKey[Unit]("Fail if spliceFast left a bare foo specifier")

checkSpliced := {
  val f = spliceFast.value
  val t = IO.read(f)
  if (t.contains("""from "foo"""") || t.contains("""require("foo")"""))
    sys.error(s"leftover foo specifier in $f")
  if (!t.contains("__splice_foo"))
    sys.error(s"expected wrapped foo module in $f")
}
