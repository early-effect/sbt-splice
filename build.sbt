val scala3Version   = "3.8.4"
val zioVersion      = "2.1.26"
val specularVersion = "0.12.1"
val scalaJsVersion  = "1.22.0"
val chekhovVersion  = "0.0.3"
val graalVersion    = "25.2.4"

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
zipxCapabilities += ZipxCentral.release
zipxCapabilities += ZipxDocs.pages()
zipxEnv := Map(
  "PLAYWRIGHT_BROWSERS_PATH" -> EnvValue.typed(Expr.github("workspace") ++ Expr.lit("/target/ms-playwright"))
)

lazy val root = project
  .in(file("."))
  .enablePlugins(SbtPlugin)
  .disablePlugins(chekhov.sbt.ChekhovPlugin)
  .aggregate(docs)
  .settings(
    name        := "sbt-splice",
    description :=
      "sbt 2 plugin: splice pinned JS into Scala.js linker output, no Node",
    scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all"),
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJsVersion),
    libraryDependencies ++= Seq(
      "dev.zio"         %% "zio"                 % zioVersion,
      "dev.zio"         %% "zio-test"            % zioVersion % Test,
      "dev.zio"         %% "zio-test-sbt"        % zioVersion % Test,
      ("io.get-coursier" % "coursier-cache_2.13" % "2.1.25-M26")
        .exclude("org.scala-lang.modules", "scala-collection-compat_2.13"),
      // sbt-scalajs does not always export IR types onto a Scala 3 plugin classpath.
      "org.scala-js" %% "scalajs-ir"                % scalaJsVersion,
      "org.scala-js" %% "scalajs-linker-interface" % scalaJsVersion,
      // Same artifact Scala.js 1.22's scalajs-linker pins.
      "com.google.javascript" % "closure-compiler" % "v20220202",
      // Test-only: prove spliced output runs on a JVM JS engine. Not published.
      "org.graalvm.polyglot" % "polyglot"        % graalVersion % Test,
      "org.graalvm.js"       % "js-language"     % graalVersion % Test,
      "org.graalvm.truffle"  % "truffle-runtime" % graalVersion % Test,
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
  .disablePlugins(chekhov.sbt.ChekhovPlugin)
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
      if (v.endsWith("-ci") || v.endsWith("-SNAPSHOT"))
        previousStableVersion.value.getOrElse("<version>")
      else
        v
    },
  )

// Browser suites. Not aggregated so local `testFull` stays Node-free; CI runs `e2e/testFull`.
lazy val e2e = project
  .in(file("e2e"))
  .dependsOn(root % "compile->compile;test->test")
  .settings(
    name           := "sbt-splice-e2e",
    publish / skip := true,
    scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all"),
    libraryDependencies ++= Seq(
      "dev.zio"           %% "zio-test"         % zioVersion     % Test,
      "dev.zio"           %% "zio-test-sbt"     % zioVersion     % Test,
      "rocks.earlyeffect" %% "chekhov-zio-test" % chekhovVersion % Test,
      "rocks.earlyeffect" %% "chekhov-driver"   % chekhovVersion % Test,
    ),
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

zipxCapabilities += Capability
  .once(
    name = Capability.TestName,
    command = zipxTasks.session(e2e / chekhovInstall, testFull, e2e / testFull, scripted),
    needsCapabilities = List(Fmt),
  )
  .withNodeVersion(NodeVersion("24"))

addCommandAlias("release", "; publishSigned; sonaRelease")
