MyVersions.settings

organization         := "rocks.earlyeffect"
organizationName     := "Early Effect"
organizationHomepage := Some(uri("https://www.earlyeffect.rocks"))
versionScheme        := Some("early-semver")

homepage := Some(uri("https://github.com/early-effect/sbt-splice"))
licenses := Seq("Apache-2.0" -> uri("http://www.apache.org/licenses/LICENSE-2.0.txt"))
scmInfo  := Some(
  ScmInfo(
    uri("https://github.com/early-effect/sbt-splice"),
    "scm:git@github.com:early-effect/sbt-splice.git",
  )
)
developers := List(
  Developer(
    id = "russwyte",
    name = "Russ White",
    email = "356303+russwyte@users.noreply.github.com",
    url = uri("https://github.com/russwyte"),
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
  scriptedLaunchOpts ++= Seq("-Xmx2g", s"-Dplugin.version=${version.value}"),
  scriptedBufferLog    := false,
  publishMavenStyle    := true,
  pomIncludeRepository := { _ => false },
)

zipxJavaVersion      := JdkVersion("25")
zipxWorkflowDispatch := true
zipxCapabilities += ZipxCentral.snapshots
zipxCapabilities += ZipxCentral.pullRequestSnapshots("snapshots")
zipxCapabilities += ZipxDocs.pages()
zipxReleaseWorkflow := Some(ZipxCentral.releases)
zipxEnv             := Map(
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
    // zipx drops org.scala-sbt off sbt-remote-cache locally; the published POM does not, so sbt
    // is re-listed as compile and compiler-interface 2.1 evicts zipx-syntax's scala3-compiler.
    addSbtPlugin(
      ("rocks.earlyeffect" % "sbt-zipx" % "0.18.1")
        .excludeAll(ExclusionRule(organization = "org.scala-sbt", name = "sbt"))
    ),
  )
  .settings(pluginSettings)
  .settings(MyVersions.spliceZipx)

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(SpecularPlugin)
  .disablePlugins(chekhov.sbt.ChekhovPlugin)
  // Cite reads `spliceFull` from this build. The published plugin does not depend on the docs project.
  // sbt is provided on the plugin, so dependsOn alone cannot read SplicePlugin's TASTy.
  .dependsOn(plugin)
  .settings(
    Test / unmanagedClasspath ++= (plugin / Compile / dependencyClasspath).value
  )
  .settings(
    name           := "sbt-splice-docs",
    publish / skip := true,
    scalacOptions ++= scalac,
    Test / mainClass      := Some("specular.site.DocsServe"),
    specularBuildMain     := "rocks.earlyeffect.splice.docs.BuildSite",
    specularMetaProject   := Some(LocalProject("plugin")),
    specularArtifactKind  := "plugin",
    specularSiteDirectory := (LocalRootProject / baseDirectory).value / "target" / "site",
    // Chrome advertises the last published tag. Development version is the ship line
    // plus -ci (0.3.3-ci). The docs workflow sets SPECULAR_STRIP_CI, and sbt-specular
    // then ignores this setting and strips that suffix, which is not on Central.
    // Update the string in the same commit that tags the next release.
    specularDisplayVersion := { raw =>
      val published = "0.3.2"
      if raw.trim == published then published else published
    },
    // The forked builder reads -Dspecular.meta.displayVersion. Replace the stripped
    // ship line with this setting's result.
    specularMetaProps := Def.uncached {
      val published = specularDisplayVersion.value("")
      val pinned    = s"-Dspecular.meta.displayVersion=$published"
      specularMetaProps.value.filterNot(_.startsWith("-Dspecular.meta.displayVersion=")) :+ pinned
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
