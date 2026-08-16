scalaVersion := "3.8.4"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }

spliceResolvers += Splice.cdn("local") { (n, v, p) =>
  "http://127.0.0.1:" + StubCdn.port + "/npm/" + n + "@" + v + "/" + p
}

spliceLibs += Splice.lib("foo", "1.0.0", "foo.js").sha256("0" * 64)
