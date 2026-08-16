scalaVersion := "3.8.4"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }

spliceResolvers += Splice.githubArchive { (owner, repo, tag) =>
  "http://127.0.0.1:" + StubGithub.port + "/" + owner + "/" + repo + "/archive/refs/tags/" + tag + ".tar.gz"
}

spliceLibs += Splice.github("foo", "owner/repo", "1.0.0", "dist/foo.js").sha256("0" * 64)
