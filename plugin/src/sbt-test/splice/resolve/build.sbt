ThisBuild / scalaVersion := "3.9.0"

lazy val checkSpliced = taskKey[Unit]("Fail if spliceFast left a bare foo specifier")
lazy val checkMaps    = taskKey[Unit]("spliceFast custom output plus source map")

def leftoverFoo(f: File): Unit =
  val t = IO.read(f)
  if (t.contains("""from "foo"""") || t.contains("""require("foo")"""))
    sys.error(s"leftover foo specifier in $f")
  if (!t.contains("__splice_foo"))
    sys.error(s"expected wrapped foo module in $f")
  ()

def importFoo = Def.settings(
  scalaJSUseMainModuleInitializer := true,
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
  Compile / scalaSource           := (LocalRootProject / baseDirectory).value / "src" / "import-foo",
)

def checkSplicedSettings = Def.settings(
  checkSpliced := Def.uncached {
    leftoverFoo(spliceFast.value)
  }
)

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .aggregate()

lazy val cdn = project
  .in(file("cdn"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    importFoo,
    spliceResolvers += Splice.cdn("local") { (n, v, p) =>
      "http://127.0.0.1:" + StubCdn.port + "/npm/" + n + "@" + v + "/" + p
    },
    spliceLibs += Splice.lib("foo", "1.0.0", "foo.js").sha256(StubCdn.sha256),
    checkSplicedSettings,
  )

lazy val cdnHash = project
  .in(file("cdn-hash"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    importFoo,
    spliceResolvers += Splice.cdn("local") { (n, v, p) =>
      "http://127.0.0.1:" + StubCdn.port + "/npm/" + n + "@" + v + "/" + p
    },
    spliceLibs += Splice.lib("foo", "1.0.0", "foo.js").sha256("0" * 64),
  )

lazy val cdnOmit = project
  .in(file("cdn-omit"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    importFoo,
    spliceLibs += Splice.lib("foo", "1.0.0", "foo.js").sha256("0" * 64),
  )

lazy val github = project
  .in(file("github"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    importFoo,
    spliceResolvers += Splice.githubArchive { (owner, repo, tag) =>
      "http://127.0.0.1:" + StubGithub.port + "/" + owner + "/" + repo + "/archive/refs/tags/" + tag + ".tar.gz"
    },
    spliceLibs += Splice.github("foo", "owner/repo", "1.0.0", "dist/foo.js").sha256(StubGithub.sha256),
    checkSplicedSettings,
  )

lazy val githubHash = project
  .in(file("github-hash"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    importFoo,
    spliceResolvers += Splice.githubArchive { (owner, repo, tag) =>
      "http://127.0.0.1:" + StubGithub.port + "/" + owner + "/" + repo + "/archive/refs/tags/" + tag + ".tar.gz"
    },
    spliceLibs += Splice.github("foo", "owner/repo", "1.0.0", "dist/foo.js").sha256("0" * 64),
  )

lazy val webjar = project
  .in(file("webjar"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    importFoo,
    resolvers += {
      val dir = (LocalRootProject / baseDirectory).value / "webjar-repo"
      WebjarRepo.write(dir)
      "webjar-stub" at dir.toURI.toASCIIString
    },
    spliceLibs += Splice.webjar("foo", "1.0.0", "foo.js"),
    checkSplicedSettings,
  )

lazy val unresolved = project
  .in(file("unresolved"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    importFoo,
  )

lazy val maps = project
  .in(file("maps"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    importFoo,
    spliceLibs += Splice.file("foo", (LocalRootProject / baseDirectory).value / "vendor" / "foo.js"),
    spliceFastOutput := baseDirectory.value / "target" / "custom" / "app.js",
    checkMaps := Def.uncached {
      leftoverFoo(spliceFast.value)
      val f = spliceFast.value
      if (!IO.read(f).contains("sourceMappingURL=app.js.map"))
        sys.error(s"expected sourceMappingURL in $f")
      val map = new File(f.getPath + ".map")
      if (!map.exists)
        sys.error(s"expected source map $map")
      val m = IO.read(map)
      if (!m.contains("\"sections\"") || !m.contains("fast-link"))
        sys.error(s"expected indexed map pointing at private-link output in $map")
      ()
    },
  )
