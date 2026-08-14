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

/** Private remapped link, then splice of pinned JS onto `__splice_*`. */
object SplicePlugin extends AutoPlugin:

  private val SpliceJs: Configuration = config("splice").hide

  object autoImport:
    val spliceLibs = settingKey[Seq[SpliceLib]](
      "Bare specifier to vendor file, WebJar, CDN, or GitHub tag tarball."
    )
    val spliceResolvers = settingKey[Seq[SpliceResolver]](
      "Where to fetch remote JS. Maven/WebJar uses project resolvers; add Splice.jsDelivr / Splice.unpkg for CDNs, Splice.github for tag tarballs."
    )
    val spliceFastOutput = settingKey[File]("Where spliceFast writes the spliced JS.")
    val spliceFullOutput = settingKey[File]("Where spliceFull writes the spliced JS.")
    val spliceFast       = taskKey[File](
      "Private-link remapped IR, then splice pinned JS (development)."
    )
    val spliceFull = taskKey[File](
      "Private-link remapped IR, splice pinned JS, then Closure-advanced (production)."
    )
    val spliceSourceMaps = settingKey[Boolean](
      "Write a source map next to the spliced JS. Default true on spliceFast, false on spliceFull."
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
    spliceFastOutput              := Def.uncached(baseDirectory.value / "target" / "splice" / "fast.js"),
    spliceFullOutput              := Def.uncached(baseDirectory.value / "target" / "splice" / "full.js"),
    spliceFast / spliceSourceMaps := true,
    spliceFull / spliceSourceMaps := false,
    spliceFast                    := Def.uncached(
      spliceTask(
        stage = fastLinkJS,
        linkDirName = "fast-link",
        out = spliceFastOutput,
        optimize = false,
      ).value
    ),
    spliceFull := Def.uncached(
      spliceTask(
        stage = fullLinkJS,
        linkDirName = "full-link",
        out = spliceFullOutput,
        optimize = true,
      ).value
    ),
  )

  private def spliceTask(
      stage: TaskKey[sbt.Attributed[org.scalajs.linker.interface.Report]],
      linkDirName: String,
      out: SettingKey[File],
      optimize: Boolean,
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
      val mapsOn     = ((if optimize then spliceFull else spliceFast) / spliceSourceMaps).value
      val stamp      =
        if optimize then Some(streams.value.cacheDirectory / "splice-full-digest") else None
      Def
        .task {
          Def.uncached {
            privateLink(irInfo.data, specs, linker, linkerImpl, inits, factory, streams.value.log, linkDir)
            runSplice(
              dir = linkDir,
              libs = libs,
              out = dest,
              resolvers = resolvers,
              report = report,
              cacheDir = cacheDir,
              localOnly = localOnly,
              extractDir = extractDir,
              optimize = optimize,
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
  ): Unit =
    IO.createDirectory(linkDir)
    val remapped           = ir.map(SpliceIR.fromIRFile(_, mapped))
    val tlog               = factory(log)
    given ExecutionContext = ExecutionContext.global
    Await.result(
      linker.link(remapped, inits, linkerImpl.outputDirectory(linkDir.toPath), tlog),
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
      optimize: Boolean,
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
        .map(f => LinkerFile(f.getName, IO.read(f), maps.get(f.getName)))
    val env = ResolveEnv(
      resolvers = resolvers,
      cacheDir = cacheDir,
      localOnly = localOnly,
      webjars = webjarJars(report, libs.collect { case w: SpliceLib.WebJar => w }),
      extractDir = extractDir,
    )
    val libMap = RunSplice(Splice.resolve(libs, env))
    val extern = libs.collect { case l if l.isExtern => l.specifier }.toSet
    val digest =
      if optimize then Some(Closure.programDigest(linker, libMap, out.toPath, extern, sourceMaps))
      else None
    val mapOut = if sourceMaps then Some(SourceMaps.mapPath(out.toPath)) else None
    val hit    = digest.exists(d => cacheStamp.exists(s => Closure.cacheHit(s.toPath, d, out.toPath, mapOut)))
    if !hit then
      RunSplice(
        Splice.run(
          SpliceInput(
            linker = linker,
            libs = libMap,
            output = out.toPath,
            optimize = optimize,
            extern = extern,
            sourceMaps = sourceMaps,
          )
        )
      )
      for
        stamp <- cacheStamp
        d     <- digest
      do Closure.storeCache(stamp.toPath, d)
    end if
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
