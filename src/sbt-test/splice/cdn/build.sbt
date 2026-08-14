scalaVersion := "3.8.4"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }

spliceResolvers += Splice.cdn("local") { (n, v, p) =>
  "http://127.0.0.1:" + StubCdn.port + "/npm/" + n + "@" + v + "/" + p
}

spliceLibs += Splice.lib("foo", "1.0.0", "foo.js").sha256(StubCdn.sha256)

lazy val checkSpliced = taskKey[Unit]("Fail if spliceFast left a bare foo specifier")

checkSpliced := {
  val f = spliceFast.value
  val t = IO.read(f)
  if (t.contains("""from "foo"""") || t.contains("""require("foo")"""))
    sys.error(s"leftover foo specifier in $f")
  if (!t.contains("__splice_foo"))
    sys.error(s"expected wrapped foo module in $f")
}
