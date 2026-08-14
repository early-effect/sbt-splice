package rocks.earlyeffect.splice.sbt

import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import rocks.earlyeffect.splice.{LinkerFile, Splice, SpliceError, SpliceInput, SpliceLib}
import sbt.*
import sbt.Keys.*
import sbt.MessageOnlyException

/** Post-link splice of pinned JS into Scala.js output. */
object SplicePlugin extends AutoPlugin:

  object autoImport:
    val spliceLibs = settingKey[Seq[SpliceLib]](
      "Bare specifier to vendored JS file (Phase 1). Later phases add Maven/CDN coordinates."
    )
    val spliceFastOutput = settingKey[File]("Where spliceFast writes the spliced JS.")
    val spliceFullOutput = settingKey[File]("Where spliceFull writes the spliced JS.")
    val spliceFast       = taskKey[File](
      "Splice pinned JS into fastLinkJS output (development)."
    )
    val spliceFull = taskKey[File](
      "Splice pinned JS into fullLinkJS output (production; Closure lands in Phase 3)."
    )
    export rocks.earlyeffect.splice.{Splice, SpliceLib}
  end autoImport

  import autoImport.*

  override def requires: Plugins      = ScalaJSPlugin
  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[Setting[?]] = Seq(
    spliceLibs := Def.uncached(Seq.empty[SpliceLib]),
    // sbt 2 `target` is `target/out/jvm/scala-…/<id>/`. Keep splice output at the
    // project-root path docs advertise (`target/splice/fast.js`).
    spliceFastOutput := Def.uncached(baseDirectory.value / "target" / "splice" / "fast.js"),
    spliceFullOutput := Def.uncached(baseDirectory.value / "target" / "splice" / "full.js"),
    spliceFast       := Def.uncached {
      val _   = (Compile / fastLinkJS).value
      val dir = (Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
      runSplice(dir, spliceLibs.value, spliceFastOutput.value)
    },
    spliceFull := Def.uncached {
      val _   = (Compile / fullLinkJS).value
      val dir = (Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value
      runSplice(dir, spliceLibs.value, spliceFullOutput.value)
    },
  )

  private def runSplice(dir: File, libs: Seq[SpliceLib], out: File): File =
    val linker =
      Option(dir.listFiles).toList.flatten
        .filter(f => f.isFile && f.getName.endsWith(".js") && !f.getName.endsWith(".map"))
        .sortBy(_.getName)
        .map(f => LinkerFile(f.getName, IO.read(f)))
    val libMap = libs.map(l => l.specifier -> l.file.toPath).toMap
    if libs.sizeIs != libMap.size then
      val dup = libs.groupBy(_.specifier).collect { case (s, xs) if xs.sizeIs > 1 => s }.head
      throw new MessageOnlyException(SpliceError.DuplicateLib(dup).message)
    RunSplice(
      Splice.run(
        SpliceInput(
          linker = linker,
          libs = libMap,
          output = out.toPath,
        )
      )
    )
    out
  end runSplice
end SplicePlugin
