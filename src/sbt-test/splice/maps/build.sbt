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
  if (!t.contains("sourceMappingURL=app.js.map"))
    sys.error(s"expected sourceMappingURL in $f")
  val map = new File(f.getPath + ".map")
  if (!map.exists)
    sys.error(s"expected source map $map")
  val m = IO.read(map)
  if (!m.contains("\"sections\"") || !m.contains("fast-link"))
    sys.error(s"expected indexed map pointing at private-link output in $map")
}
