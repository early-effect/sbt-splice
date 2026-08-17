import zipx.*

/** Typed catalog: every library and sbt plugin this build may use. `zipxDepUpdate` rewrites constructors here.
  *
  * Every `Lib` / `Plugin` val is a catalog row. Each module selects a group (`plugin`, `docs`, `e2e`, `spliceZipx`).
  */
object MyVersions extends ZipxVersions:

  val sbt: SbtVersion     = SbtVersion("2.0.6")
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

  val specular: Lib        = Lib("rocks.earlyeffect", "specular-core", "0.12.1").test
  val specularZioTest: Lib = specular.mod("specular-zio-test")
  val specularSite: Lib    = specular.mod("specular-site")
  val specularTheme: Lib   = specular.mod("early-effect-docs-theme")

  val chekhovZioTest: Lib = Lib("rocks.earlyeffect", "chekhov-zio-test", "0.0.3").test
  val chekhovDriver: Lib  = chekhovZioTest.mod("chekhov-driver")

  val scalafmt: Plugin       = Plugin("org.scalameta", "sbt-scalafmt", "2.6.2")
  val dynverCi: Plugin       = Plugin("rocks.earlyeffect", "sbt-dynver-ci", "0.2.2")
  val specularPlugin: Plugin = Plugin("rocks.earlyeffect", "sbt-specular", "0.12.1")
  val chekhovPlugin: Plugin  = Plugin("rocks.earlyeffect", "sbt-chekhov", "0.0.3")

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
  def docs       = library(specular, specularZioTest, specularSite, specularTheme, zioTest, zioTestSbt)
  def e2e        = library(zioTest, zioTestSbt, chekhovZioTest, chekhovDriver)
  def spliceZipx = library(zioTest, zioTestSbt)
end MyVersions
