import zipx.*

object MyVersions extends ZipxVersions:
  val sbt: SbtVersion     = SbtVersion("2.0.6")
  val scala: ScalaVersion = ScalaVersion("3.8.4")
  val scalafmt            = Plugin("org.scalameta", "sbt-scalafmt", "2.6.2")
  val scalajs             = Plugin("org.scala-js", "sbt-scalajs", "1.22.0")
  val foo                 = Pin("splice", "foo", "1.0.0", sha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", purl = "pkg:npm/foo@1.0.0")
