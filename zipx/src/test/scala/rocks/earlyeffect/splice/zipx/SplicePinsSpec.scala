package rocks.earlyeffect.splice.zipx

import rocks.earlyeffect.splice.Splice
import rocks.earlyeffect.splice.SpliceLib.sha256
import zipx.core.*
import zio.test.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

object SplicePinsSpec extends ZIOSpecDefault:

  def spec =
    suite("SplicePins")(
      test("inventory skips vendor files") {
        val file = Splice.file("foo", new java.io.File("vendor/foo.js"))
        assertTrue(SplicePins.inventory(Seq(file)).isEmpty)
      },
      test("CDN and GitHub pins carry a PURL; WebJar is advisory-only") {
        val cdn    = Splice.lib("preact", "10.26.4", "dist/preact.module.js").sha256("ab")
        val github = Splice.github("foo", "owner/repo", "1.2.3", "dist/foo.js").sha256("cd")
        val webjar = Splice.webjar("htm", "3.1.4", "dist/htm.module.js")
        val pins   = SplicePins.inventory(Seq(cdn, github, webjar))
        val byId   = pins.map(p => p.id -> p).toMap
        assertTrue(
          byId("preact").current == "10.26.4",
          byId("preact").purl.contains(Purl("pkg:npm/preact@10.26.4")),
          byId("foo").current == "1.2.3",
          byId("foo").purl.contains(Purl("pkg:github/owner/repo@1.2.3")),
          byId("htm").current == "3.1.4",
          byId("htm").purl.contains(Purl("pkg:npm/htm@3.1.4")),
        )
      },
      test("no feed when every library is a vendor file") {
        val file = Splice.file("foo", new java.io.File("vendor/foo.js"))
        val got  =
          SplicePins.feed(Seq(file), new java.io.File("."), SplicePins.constLookup("9"), SplicePins.constSha("x"))
        assertTrue(got.isEmpty)
      },
      test("apply rewrites a unique version and sha256 together") {
        val dir = Files.createTempDirectory("splice-pins")
        val src = dir.resolve("build.sbt")
        write(
          src,
          """spliceLibs += Splice.lib("foo", "1.0.0", "foo.js").sha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
""",
        )
        val lib = Splice
          .lib("foo", "1.0.0", "foo.js")
          .sha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        val feed = SplicePins
          .feed(
            Seq(lib),
            dir.toFile,
            SplicePins.constLookup("1.0.1"),
            SplicePins.constSha("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
          )
          .head
        val applied = feed.apply(feed.inventory.head, "1.0.1")
        val text    = Files.readString(src)
        assertTrue(
          applied.isRight,
          text.contains("""Splice.lib("foo", "1.0.1", "foo.js")"""),
          text.contains("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
          !text.contains("1.0.0"),
        )
      },
      test("apply fails when the version string is not unique") {
        val dir = Files.createTempDirectory("splice-pins-dup")
        write(
          dir.resolve("build.sbt"),
          """spliceLibs += Splice.lib("foo", "1.0.0", "foo.js").sha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
spliceLibs += Splice.lib("bar", "1.0.0", "bar.js").sha256("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc")
""",
        )
        val lib = Splice
          .lib("foo", "1.0.0", "foo.js")
          .sha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        val feed = SplicePins
          .feed(Seq(lib), dir.toFile, SplicePins.constLookup("1.0.1"), SplicePins.constSha("bb"))
          .head
        val applied = feed.apply(feed.inventory.head, "1.0.1")
        assertTrue(applied.left.exists(_.contains("not unique")))
      },
      test("apply skips a WebJar rewrite") {
        val dir = Files.createTempDirectory("splice-pins-webjar")
        val src = dir.resolve("build.sbt")
        write(
          src,
          """spliceLibs += Splice.webjar("htm", "3.1.4", "dist/htm.module.js")
""",
        )
        val before = Files.readString(src)
        val feed   = SplicePins
          .feed(
            Seq(Splice.webjar("htm", "3.1.4", "dist/htm.module.js")),
            dir.toFile,
            SplicePins.constLookup("3.1.5"),
            SplicePins.constSha("nope"),
          )
          .head
        val applied = feed.apply(feed.inventory.head, "3.1.5")
        assertTrue(applied.isRight, Files.readString(src) == before)
      },
      test("classify uses npm for semver and exact otherwise") {
        assertTrue(
          SplicePins.classify.classify("1.2.3", "1.2.4") == BumpKind.Patch,
          SplicePins.classify.classify("1.2.3", "2.0.0") == BumpKind.Major,
          SplicePins.classify.classify("nightly", "next") == BumpKind.Major,
          SplicePins.classify.classify("nightly", "nightly") == BumpKind.None,
        )
      },
    )

  private def write(path: Path, text: String): Unit =
    Files.writeString(path, text, StandardCharsets.UTF_8)
    ()
end SplicePinsSpec
