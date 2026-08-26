import zipx.*

/** Typed catalog: every library and sbt plugin this build may use. `zipxDepUpdate` rewrites constructors here.
  *
  * sbt-zipx is not a row: generate emits it from the loaded plugin (`zipxSelfPlugins`). sbt-pgp is not a row: zipx
  * already brings it in.
  *
  * Every `Lib` / `Plugin` / `Action` val is a catalog row. Each module selects a group (`plugin`, `docs`, `e2e`,
  * `spliceZipx`).
  */
object MyVersions extends ZipxVersions:

  val sbt: SbtVersion     = SbtVersion("2.0.7")
  val scala: ScalaVersion = ScalaVersion("3.8.4")

  val zio: Lib        = Lib("dev.zio", "zio", "2.1.26")
  val zioTest: Lib    = zio.mod("zio-test").test
  val zioTestSbt: Lib = zio.mod("zio-test-sbt").test

  val scalajsIr: Lib     = Lib("org.scala-js", "scalajs-ir", "1.22.0")
  val scalajsLinker: Lib = scalajsIr.mod("scalajs-linker-interface")

  // Our post-link pin, not Scala.js's. scalajs-linker still declares v20220202; this evicts it.
  val closure: Lib = Lib("com.google.javascript", "closure-compiler", "v20260726").java

  val compress: Lib = Lib("org.apache.commons", "commons-compress", "1.28.0").java

  val coursier: Lib = Lib("io.get-coursier", "coursier-cache_2.13", "2.1.25-M26").java
    .excluding(ZipxExclude.org("org.scala-lang.modules", "scala-collection-compat_2.13"))

  val graalPolyglot: Lib = Lib("org.graalvm.polyglot", "polyglot", "25.2.4").java.test
  val graalJs: Lib       = Lib("org.graalvm.js", "js-language", "25.2.4").java.test
  val graalTruffle: Lib  = Lib("org.graalvm.truffle", "truffle-runtime", "25.2.4").java.test

  val specular: Lib        = Lib("rocks.earlyeffect", "specular-core", "0.14.1")
  val specularZioTest: Lib = specular.mod("specular-zio-test").test
  val specularTheme: Lib   = specular.mod("early-effect-docs-theme").test

  val chekhovZioTest: Lib = Lib("rocks.earlyeffect", "chekhov-zio-test", "0.0.5").test
  val chekhovDriver: Lib  = chekhovZioTest.mod("chekhov-driver")

  val scalafmt: Plugin       = Plugin("org.scalameta", "sbt-scalafmt", "2.6.2")
  val dynverCi: Plugin       = Plugin("rocks.earlyeffect", "sbt-dynver-ci", "0.2.3")
  val specularPlugin: Plugin = Plugin("rocks.earlyeffect", "sbt-specular", "0.14.1")
  val chekhovPlugin: Plugin  = Plugin("rocks.earlyeffect", "sbt-chekhov", "0.0.5")

  val checkout: Action =
    Action("actions/checkout", "v7.0.1", sha = "3d3c42e5aac5ba805825da76410c181273ba90b1")
  val setupJava: Action =
    Action("actions/setup-java", "v6.0.0", sha = "dd06d9cba3e5552c54d9f8ea23572deb30010f7c")
  val setupSbt: Action =
    Action("sbt/setup-sbt", "v1.5.8", sha = "c7d2d6258b4bd0d3ec5129e6b3453199d3c79729")
  val setupNode: Action =
    Action("actions/setup-node", "v7.0.0", sha = "820762786026740c76f36085b0efc47a31fe5020")
  val cache: Action =
    Action("actions/cache", "v6.1.0", sha = "55cc8345863c7cc4c66a329aec7e433d2d1c52a9")
  val uploadArtifact: Action =
    Action("actions/upload-artifact", "v7.0.1", sha = "043fb46d1a93c77aae656e7c1c64a875d1fc6a0a")
  val downloadArtifact: Action =
    Action("actions/download-artifact", "v8.0.1", sha = "3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c")

  def plugin = library(
    zio,
    zioTest,
    zioTestSbt,
    coursier,
    scalajsIr,
    scalajsLinker,
    closure,
    compress,
    graalPolyglot,
    graalJs,
    graalTruffle,
  )
  def docs       = library(specularZioTest, specularTheme)
  def e2e        = library(chekhovZioTest, chekhovDriver)
  def spliceZipx = library(zioTest, zioTestSbt)
end MyVersions
