package rocks.earlyeffect.splice.sbt

import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import rocks.earlyeffect.splice.*
import _root_.sbt.*
import _root_.sbt.Keys.*

import java.nio.file.Path

/** Post-link splice of pinned JS into Scala.js output. */
object SplicePlugin extends AutoPlugin:

  private val SpliceJs: Configuration = config("splice").hide

  object autoImport:
    val spliceLibs = settingKey[Seq[SpliceLib]](
      "Bare specifier to vendor file, WebJar, or CDN coordinate."
    )
    val spliceResolvers = settingKey[Seq[SpliceResolver]](
      "Where to fetch remote JS. Maven/WebJar uses project resolvers; add Splice.jsDelivr / Splice.unpkg for CDNs."
    )
    val spliceFastOutput = settingKey[File]("Where spliceFast writes the spliced JS.")
    val spliceFullOutput = settingKey[File]("Where spliceFull writes the spliced JS.")
    val spliceFast       = taskKey[File](
      "Splice pinned JS into fastLinkJS output (development)."
    )
    val spliceFull = taskKey[File](
      "Splice pinned JS into fullLinkJS output (production; Closure lands in Phase 3)."
    )
    export rocks.earlyeffect.splice.{Splice, SpliceLib, SpliceResolver}
    export rocks.earlyeffect.splice.SpliceLib.sha256

    extension (s: Splice.type)
      def webjar(specifier: String, module: ModuleID, path: String): SpliceLib.WebJar =
        Splice.webjar(specifier, module.organization, module.name, module.revision, path)
  end autoImport

  import autoImport.*

  override def requires: Plugins      = ScalaJSPlugin
  override def trigger: PluginTrigger = allRequirements

  override def projectConfigurations: Seq[Configuration] = Seq(SpliceJs)

  override def projectSettings: Seq[Setting[?]] = Seq(
    spliceLibs      := Def.uncached(Seq.empty[SpliceLib]),
    spliceResolvers := Def.uncached(Seq(Splice.maven)),
    ivyConfigurations += SpliceJs,
    libraryDependencies ++= {
      val mavenOn = spliceResolvers.value.exists {
        case SpliceResolver.Maven => true
        case _                    => false
      }
      if mavenOn then
        spliceLibs.value.collect { case w: SpliceLib.WebJar =>
          w.organization % w.name % w.version % SpliceJs
        }
      else Nil
    },
    // sbt 2 `target` is `target/out/jvm/scala-…/<id>/`. Keep splice output at the
    // project-root path docs advertise (`target/splice/fast.js`).
    spliceFastOutput := Def.uncached(baseDirectory.value / "target" / "splice" / "fast.js"),
    spliceFullOutput := Def.uncached(baseDirectory.value / "target" / "splice" / "full.js"),
    spliceFast       := Def.uncached {
      val _   = (Compile / fastLinkJS).value
      val dir = (Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
      runSplice(
        dir = dir,
        libs = spliceLibs.value,
        out = spliceFastOutput.value,
        resolvers = spliceResolvers.value,
        report = update.value,
        cacheDir = csrCacheDirectory.value.toPath,
        localOnly = offline.value,
        extractDir = (baseDirectory.value / "target" / "splice" / "extracted").toPath,
      )
    },
    spliceFull := Def.uncached {
      val _   = (Compile / fullLinkJS).value
      val dir = (Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value
      runSplice(
        dir = dir,
        libs = spliceLibs.value,
        out = spliceFullOutput.value,
        resolvers = spliceResolvers.value,
        report = update.value,
        cacheDir = csrCacheDirectory.value.toPath,
        localOnly = offline.value,
        extractDir = (baseDirectory.value / "target" / "splice" / "extracted").toPath,
      )
    },
  )

  private def runSplice(
      dir: File,
      libs: Seq[SpliceLib],
      out: File,
      resolvers: Seq[SpliceResolver],
      report: UpdateReport,
      cacheDir: Path,
      localOnly: Boolean,
      extractDir: Path,
  ): File =
    val linker =
      Option(dir.listFiles).toList.flatten
        .filter(f => f.isFile && f.getName.endsWith(".js") && !f.getName.endsWith(".map"))
        .sortBy(_.getName)
        .map(f => LinkerFile(f.getName, IO.read(f)))
    val env = ResolveEnv(
      resolvers = resolvers,
      cacheDir = cacheDir,
      localOnly = localOnly,
      webjars = webjarJars(report, libs.collect { case w: SpliceLib.WebJar => w }),
      extractDir = extractDir,
    )
    val libMap = RunSplice(Splice.resolve(libs, env))
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

  private def webjarJars(report: UpdateReport, webjars: Seq[SpliceLib.WebJar]): Map[String, Path] =
    val modules =
      report.configurations.iterator
        .filter(_.configuration.name == SpliceJs.name)
        .flatMap(_.modules)
        .toList
    webjars.flatMap { w =>
      val file = modules
        .find { m =>
          val mid = m.module
          mid.organization == w.organization &&
          mid.name == w.name &&
          mid.revision == w.version
        }
        .flatMap(m => m.artifacts.collectFirst { case (_, f) => f })
      file.map(f => w.specifier -> f.toPath)
    }.toMap
  end webjarJars
end SplicePlugin
