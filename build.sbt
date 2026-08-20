MyVersions.settings

organization         := "rocks.earlyeffect"
organizationName     := "Early Effect"
organizationHomepage := Some(url("https://www.earlyeffect.rocks"))
versionScheme        := Some("early-semver")

homepage := Some(url("https://github.com/early-effect/sbt-splice"))
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
scmInfo  := Some(
  ScmInfo(
    url("https://github.com/early-effect/sbt-splice"),
    "scm:git@github.com:early-effect/sbt-splice.git",
  )
)
developers := List(
  Developer(
    id = "russwyte",
    name = "Russ White",
    email = "356303+russwyte@users.noreply.github.com",
    url = url("https://github.com/russwyte"),
  )
)

// Sonatype Central Portal. sbt 2 has localStaging / publishSigned / sonaRelease.
publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}

// CI-only publishing: key hex from PGP_KEY_HEX (org secret). Sentinel keeps local loads working.
usePgpKeyHex(sys.env.getOrElse("PGP_KEY_HEX", "MISSING_KEY_HEX"))

val scalac = Seq("-deprecation", "-feature", "-Wunused:all")

val pluginSettings = Seq(
  scalacOptions ++= scalac,
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  scriptedLaunchOpts ++= Seq("-Xmx512m", s"-Dplugin.version=${version.value}"),
  scriptedBufferLog    := false,
  publishMavenStyle    := true,
  pomIncludeRepository := { _ => false },
)

zipxJavaVersion      := JdkVersion("25")
zipxWorkflowDispatch := true
zipxCapabilities += ZipxCentral.release
zipxCapabilities += ZipxDocs.pages()
zipxEnv := Map(
  "PLAYWRIGHT_BROWSERS_PATH" -> EnvValue.typed(Expr.github("workspace") ++ Expr.lit("/target/ms-playwright"))
)

lazy val root = project
  .in(file("."))
  .disablePlugins(chekhov.sbt.ChekhovPlugin)
  .aggregate(plugin, spliceZipx, docs)
  .settings(
    name           := "sbt-splice-root",
    publish / skip := true,
  )

lazy val plugin = project
  .in(file("plugin"))
  .enablePlugins(SbtPlugin)
  .disablePlugins(chekhov.sbt.ChekhovPlugin)
  .settings(
    name        := "sbt-splice",
    description :=
      "sbt 2 plugin: splice pinned JS into Scala.js linker output, no Node",
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % (MyVersions.scalajsIr.version: String)),
  )
  .settings(pluginSettings)
  .settings(MyVersions.plugin)

lazy val spliceZipx = project
  .in(file("zipx"))
  .enablePlugins(SbtPlugin)
  .disablePlugins(chekhov.sbt.ChekhovPlugin)
  .dependsOn(plugin)
  .settings(
    name        := "sbt-splice-zipx",
    description := "Opt-in zipx pin feed for sbt-splice library pins",
    addSbtPlugin("rocks.earlyeffect" % "sbt-zipx" % "0.7.2"),
  )
  .settings(pluginSettings)
  .settings(MyVersions.spliceZipx)

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(SpecularPlugin)
  .disablePlugins(chekhov.sbt.ChekhovPlugin)
  .settings(
    name           := "sbt-splice-docs",
    publish / skip := true,
    scalacOptions ++= scalac,
    Test / mainClass       := Some("specular.site.DocsServe"),
    specularBuildMain      := "rocks.earlyeffect.splice.docs.BuildSite",
    specularMetaProject    := Some(LocalProject("plugin")),
    specularArtifactKind   := "plugin",
    specularSiteDirectory  := (LocalRootProject / baseDirectory).value / "target" / "site",
    specularDisplayVersion := {
      val v = (ThisBuild / version).value
      if (v.endsWith("-ci") || v.endsWith("-SNAPSHOT"))
        previousStableVersion.value.getOrElse("<version>")
      else
        v
    },
  )
  .settings(MyVersions.docs)

// Browser suites. Not aggregated so local `testFull` stays Node-free; CI runs `e2e/testFull`.
lazy val e2e = project
  .in(file("e2e"))
  .dependsOn(plugin % "compile->compile;test->test")
  .settings(
    name           := "sbt-splice-e2e",
    publish / skip := true,
    scalacOptions ++= scalac,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    chekhovBrowser := "firefox",
    // sbt-chekhov 0.0.3 installs every engine; this suite only smokes Firefox.
    chekhovInstall := Def.uncached {
      val log     = streams.value.log
      val name    = chekhovBrowser.value
      val browser = chekhov.ChekhovBrowser
        .fromString(name)
        .getOrElse(
          sys.error(s"chekhov: unknown browser '$name'")
        )
      chekhov.protocol.PinnedPlaywright.install(
        browsers = List(browser),
        log = msg => log.info(msg),
      ) match {
        case Left(err)  => sys.error(err)
        case Right(cli) =>
          log.info(s"Pinned Playwright ${chekhov.protocol.PinnedPlaywright.version} CLI: $cli")
      }
    },
  )
  .settings(MyVersions.e2e)

zipxCapabilities += Capability
  .once(
    name = Capability.TestName,
    command = zipxTasks.session(
      e2e / chekhovInstall,
      testFull,
      e2e / testFull,
      plugin / scripted,
      spliceZipx / scripted,
    ),
  )
  .withNodeVersion(NodeVersion("24"))

addCommandAlias("release", "; plugin/publishSigned; spliceZipx/publishSigned; sonaRelease")
