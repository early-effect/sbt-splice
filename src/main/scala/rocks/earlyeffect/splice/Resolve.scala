package rocks.earlyeffect.splice

import coursier.cache.{CachePolicy, FileCache}
import coursier.util.Artifact
import zio.*

import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import java.util.zip.ZipFile
import scala.concurrent.ExecutionContext
import scala.util.Using

/** Turn spliceLibs coordinates into files. CDN goes through Coursier FileCache. */
object Resolve:

  def files(libs: Seq[SpliceLib], env: ResolveEnv): IO[SpliceError, Map[String, Path]] =
    val dups = libs.groupBy(_.specifier).collect { case (s, xs) if xs.sizeIs > 1 => s }
    dups.headOption match
      case Some(s) => ZIO.fail(SpliceError.DuplicateLib(s))
      case None    =>
        ZIO.foreach(libs)(lib => one(lib, env).map(lib.specifier -> _)).map(_.toMap)

  private def one(lib: SpliceLib, env: ResolveEnv): IO[SpliceError, Path] =
    lib match
      case SpliceLib.File(spec, file, _) =>
        val path = file.toPath
        ZIO.unless(Files.isRegularFile(path))(ZIO.fail(SpliceError.MissingFile(spec, path.toString))).as(path)
      case c: SpliceLib.Cdn    => cdn(c, env)
      case w: SpliceLib.WebJar => webjar(w, env)

  private def cdn(lib: SpliceLib.Cdn, env: ResolveEnv): IO[SpliceError, Path] =
    val pin = lib.sha256.map(_.trim.toLowerCase).filter(_.nonEmpty)
    pin match
      case None           => ZIO.fail(SpliceError.MissingSha256(lib.specifier))
      case Some(expected) =>
        val cdns = env.resolvers.collect { case c: SpliceResolver.Cdn => c }
        if cdns.isEmpty then ZIO.fail(SpliceError.NoResolver(lib.specifier, "cdn"))
        else fetchFirst(lib, expected, cdns.toList, env)

  private def fetchFirst(
      lib: SpliceLib.Cdn,
      expected: String,
      cdns: List[SpliceResolver.Cdn],
      env: ResolveEnv,
  ): IO[SpliceError, Path] =
    cdns match
      case Nil =>
        ZIO.fail(
          SpliceError.NotFound(
            lib.specifier,
            s"${lib.name}@${lib.version}/${lib.path}",
          )
        )
      case c :: rest =>
        val url = c.expand(lib.name, lib.version, lib.path.stripPrefix("/"))
        fetch(url, env).foldZIO(
          {
            case SpliceError.NotFound(_, _) if rest.nonEmpty =>
              fetchFirst(lib, expected, rest, env)
            case other => ZIO.fail(other)
          },
          path =>
            val actual = sha256(path)
            if actual == expected then ZIO.succeed(path)
            else ZIO.fail(SpliceError.ChecksumMismatch(lib.specifier, expected, actual)),
        )

  private def fetch(url: String, env: ResolveEnv): IO[SpliceError, Path] =
    ZIO
      .attemptBlocking {
        given ExecutionContext = ExecutionContext.global
        val policies           =
          if env.localOnly then Seq(CachePolicy.LocalOnly)
          else Seq(CachePolicy.FetchMissing)
        val cache = FileCache()
          .withLocation(env.cacheDir.toFile)
          .withChecksums(Seq(None))
          .withCachePolicies(policies)
        cache.file(Artifact(url)).run.unsafeRun()
      }
      .mapError(e => SpliceError.Io(s"coursier fetch $url: ${e.getMessage}"))
      .flatMap {
        case Left(err) => ZIO.fail(SpliceError.NotFound(url, err.describe))
        case Right(f)  => ZIO.succeed(f.toPath)
      }

  private def webjar(lib: SpliceLib.WebJar, env: ResolveEnv): IO[SpliceError, Path] =
    val mavenOn = env.resolvers.exists {
      case SpliceResolver.Maven => true
      case _                    => false
    }
    if !mavenOn then ZIO.fail(SpliceError.NoResolver(lib.specifier, "maven"))
    else
      env.webjars.get(lib.specifier) match
        case None =>
          ZIO.fail(
            SpliceError.NotFound(
              lib.specifier,
              s"${lib.organization}:${lib.name}:${lib.version}",
            )
          )
        case Some(jar) => extract(lib, jar, env.extractDir)
    end if
  end webjar

  private def extract(lib: SpliceLib.WebJar, jar: Path, destDir: Path): IO[SpliceError, Path] =
    val entry = s"META-INF/resources/webjars/${lib.name}/${lib.version}/${lib.path.stripPrefix("/")}"
    if entry.contains("..") then ZIO.fail(SpliceError.Io(s"illegal webjar path ${lib.path}"))
    else
      ZIO
        .attemptBlocking {
          Using.resource(new ZipFile(jar.toFile)): zf =>
            Option(zf.getEntry(entry)) match
              case None    => Left(SpliceError.MissingJarPath(lib.specifier, jar.toString, entry))
              case Some(e) =>
                val dest = destDir.resolve(s"${JsModules.ident(lib.specifier)}.js")
                Files.createDirectories(dest.getParent)
                Using.resource(zf.getInputStream(e)): in =>
                  Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING)
                Right(dest)
        }
        .mapError(e => SpliceError.Io(s"could not read ${jar}: ${e.getMessage}"))
        .flatMap {
          case Left(err) => ZIO.fail(err)
          case Right(p)  => ZIO.succeed(p)
        }
    end if
  end extract

  def sha256(path: Path): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(Files.readAllBytes(path))
      .map("%02x".format(_))
      .mkString
end Resolve
