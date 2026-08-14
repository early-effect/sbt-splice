val scala3Version   = "3.8.4"
val zioVersion      = "2.1.26"
val specularVersion = "0.12.1"
val scalaJsVersion  = "1.22.0"

scalaVersion         := scala3Version
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

val Fmt = CapabilityName("fmt")

zipxJavaVersion      := JdkVersion("25")
zipxWorkflowDispatch := true
zipxScalaSteward     := true
zipxCapabilities += zipxTasks.once(Fmt, scalafmtCheckAll)
zipxCapabilities += Capability.once(
  name = Capability.TestName,
  command = zipxTasks.session(testFull, scripted),
  needsCapabilities = List(Fmt),
)
zipxCapabilities += ZipxCentral.release
zipxCapabilities += ZipxDocs.pages()

lazy val root = project
  .in(file("."))
  .enablePlugins(SbtPlugin)
  .aggregate(docs)
  .settings(
    name        := "sbt-splice",
    description :=
      "sbt 2 plugin: splice pinned JS into Scala.js linker output, no Node",
    scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all"),
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJsVersion),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"          % zioVersion,
      "dev.zio" %% "zio-test"     % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    scriptedLaunchOpts ++= Seq("-Xmx512m", s"-Dplugin.version=${version.value}"),
    scriptedBufferLog    := false,
    publishMavenStyle    := true,
    pomIncludeRepository := { _ => false },
  )

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(SpecularPlugin)
  .settings(
    name           := "sbt-splice-docs",
    publish / skip := true,
    scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all"),
    libraryDependencies ++= Seq(
      "rocks.earlyeffect" %% "specular-core"           % specularVersion % Test,
      "rocks.earlyeffect" %% "specular-zio-test"       % specularVersion % Test,
      "rocks.earlyeffect" %% "specular-site"           % specularVersion % Test,
      "rocks.earlyeffect" %% "early-effect-docs-theme" % specularVersion % Test,
      "dev.zio"           %% "zio-test"                % zioVersion      % Test,
      "dev.zio"           %% "zio-test-sbt"            % zioVersion      % Test,
    ),
    Test / mainClass       := Some("specular.site.DocsServe"),
    specularBuildMain      := "rocks.earlyeffect.splice.docs.BuildSite",
    specularMetaProject    := Some(LocalProject("root")),
    specularArtifactKind   := "plugin",
    specularSiteDirectory  := (LocalRootProject / baseDirectory).value / "target" / "site",
    specularDisplayVersion := {
      val v = (ThisBuild / version).value
      if (v.endsWith("-ci") || v.endsWith("-SNAPSHOT")) then {
        previousStableVersion.value.getOrElse("<version>")
      }
      else {
        v
      }
    },
  )

addCommandAlias("release", "; publishSigned; sonaRelease")
