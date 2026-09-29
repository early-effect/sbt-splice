package rocks.earlyeffect.splice

import org.scalajs.linker.interface.{ClearableLinker, IRFile}
import org.scalajs.logging.Logger as SJSLogger
import org.scalajs.sbtplugin.LinkerImpl
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import sbt.*
import sbt.Keys.*

import java.nio.file.Path
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

/** Link, then splice pinned JS. `spliceFast` and `spliceFull` bundle the linker's imports. `spliceClosure` rewrites
  * those imports to `__splice_*` globals first.
  */
object SplicePlugin extends AutoPlugin:

  private val SpliceJs: Configuration = config("splice").hide

  object autoImport:
    val spliceLibs = settingKey[Seq[SpliceLib]](
      "Bare specifier to vendor file, WebJar, CDN, or GitHub tag tarball."
    )
    val spliceResolvers = settingKey[Seq[SpliceResolver]](
      "Where to fetch remote JS. Maven/WebJar uses project resolvers; add Splice.jsDelivr / Splice.unpkg for CDNs, Splice.github for tag tarballs."
    )
    val spliceFastOutput    = settingKey[File]("Where spliceFast writes the spliced JS.")
    val spliceFullOutput    = settingKey[File]("Where spliceFull writes the spliced JS.")
    val spliceClosureOutput = settingKey[File]("Where spliceClosure writes the spliced JS.")
    val spliceFast          = taskKey[File](
      "Link, then bundle the linker's imports with pinned esbuild (development)."
    )
    val spliceFull = taskKey[File](
      "Link, then bundle and minify the linker's imports with pinned esbuild (production)."
    )
    val spliceClosure = taskKey[File](
      "Link with imports rewritten to __splice_* globals, then Closure advanced on Scala.js output."
    )
    val spliceSourceMaps = settingKey[Boolean](
      "Write a source map next to the spliced JS. Default true on spliceFast, false on spliceFull and spliceClosure."
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
    spliceFastOutput                 := Def.uncached(baseDirectory.value / "target" / "splice" / "fast.js"),
    spliceFullOutput                 := Def.uncached(baseDirectory.value / "target" / "splice" / "full.js"),
    spliceClosureOutput              := Def.uncached(baseDirectory.value / "target" / "splice" / "closure.js"),
    spliceFast / spliceSourceMaps    := true,
    spliceFull / spliceSourceMaps    := false,
    spliceClosure / spliceSourceMaps := false,
    spliceFast                       := Def.uncached(
      spliceTask(
        stage = fastLinkJS,
        linkDirName = "fast-link",
        out = spliceFastOutput,
        minify = Minify.None,
      ).value
    ),
    spliceFull := Def.uncached(
      spliceTask(
        stage = fullLinkJS,
        linkDirName = "full-link",
        out = spliceFullOutput,
        minify = Minify.Esbuild,
      ).value
    ),
    spliceClosure := Def.uncached(
      spliceTask(
        stage = fullLinkJS,
        linkDirName = "full-link",
        out = spliceClosureOutput,
        minify = Minify.Closure,
      ).value
    ),
  )

  private def spliceTask(
      stage: TaskKey[sbt.Attributed[org.scalajs.linker.interface.Report]],
      linkDirName: String,
      out: SettingKey[File],
      minify: Minify,
  ): Def.Initialize[Task[File]] =
    Def.taskDyn {
      val irInfo     = (Compile / scalaJSIR).value
      val linker     = (Compile / stage / scalaJSLinker).value
      val linkerImpl = (Compile / stage / scalaJSLinkerImpl).value
      val usesTag    = (Compile / stage / usesScalaJSLinkerTag).value
      val inits      = (Compile / scalaJSModuleInitializers).value
      val factory    = scalaJSLoggerFactory.value
      val specs      = spliceLibs.value.map(_.specifier).toSet
      val libs       = spliceLibs.value
      val dest       = out.value
      val resolvers  = spliceResolvers.value
      val report     = update.value
      val cacheDir   = csrCacheDirectory.value.toPath
      val localOnly  = offline.value
      val extractDir = (baseDirectory.value / "target" / "splice" / "extracted").toPath
      val linkDir    = baseDirectory.value / "target" / "splice" / linkDirName
      val mapsOn     = minify match
        case Minify.None    => (spliceFast / spliceSourceMaps).value
        case Minify.Esbuild => (spliceFull / spliceSourceMaps).value
        case Minify.Closure => (spliceClosure / spliceSourceMaps).value
      val stamp = minify match
        case Minify.None    => None
        case Minify.Esbuild => Some(streams.value.cacheDirectory / "splice-full-digest")
        case Minify.Closure => Some(streams.value.cacheDirectory / "splice-closure-digest")
      Def
        .task {
          Def.uncached {
            privateLink(
              irInfo.data,
              specs,
              linker,
              linkerImpl,
              inits,
              factory,
              streams.value.log,
              linkDir,
              minify,
            )
            runSplice(
              dir = linkDir,
              libs = libs,
              out = dest,
              resolvers = resolvers,
              report = report,
              cacheDir = cacheDir,
              localOnly = localOnly,
              extractDir = extractDir,
              minify = minify,
              cacheStamp = stamp,
              sourceMaps = mapsOn,
            )
          }
        }
        .tag(usesTag)
    }

  private def privateLink(
      ir: Seq[IRFile],
      mapped: Set[String],
      linker: ClearableLinker,
      linkerImpl: LinkerImpl,
      inits: Seq[org.scalajs.linker.interface.ModuleInitializer],
      factory: sbt.Logger => SJSLogger,
      log: sbt.Logger,
      linkDir: File,
      minify: Minify,
  ): Unit =
    IO.createDirectory(linkDir)
    val linked = minify match
      case Minify.Closure               => ir.map(SpliceIR.fromIRFile(_, mapped))
      case Minify.None | Minify.Esbuild => ir
    val tlog               = factory(log)
    given ExecutionContext = ExecutionContext.global
    Await.result(
      linker.link(linked, inits, linkerImpl.outputDirectory(linkDir.toPath), tlog),
      Duration.Inf,
    )
    ()
  end privateLink

  private def runSplice(
      dir: File,
      libs: Seq[SpliceLib],
      out: File,
      resolvers: Seq[SpliceResolver],
      report: UpdateReport,
      cacheDir: Path,
      localOnly: Boolean,
      extractDir: Path,
      minify: Minify,
      cacheStamp: Option[File],
      sourceMaps: Boolean,
  ): File =
    val files = Option(dir.listFiles).toList.flatten.filter(_.isFile)
    val maps  = files
      .filter(_.getName.endsWith(".js.map"))
      .map(f => f.getName.stripSuffix(".map") -> f.toPath)
      .toMap
    val linker =
      files
        .filter(f => f.getName.endsWith(".js") && !f.getName.endsWith(".map"))
        .sortBy(_.getName)
        .map(f => LinkerFile(f.getName, IO.read(f), maps.get(f.getName), Some(f.toPath)))
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
          minify = minify,
          sourceMaps = sourceMaps,
          cacheDir = cacheDir,
          localOnly = localOnly,
          cache = cacheStamp.map(_.toPath),
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
