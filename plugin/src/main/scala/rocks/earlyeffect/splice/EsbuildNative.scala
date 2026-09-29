package rocks.earlyeffect.splice

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import zio.*

import java.io.BufferedInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.matching.Regex

/** Pinned native esbuild, which bundles the mapped libraries and minifies `spliceFull`. Fetched through Coursier the
  * first time a build needs it, never committed.
  */
object EsbuildNative:

  val Version: String = "0.28.2"

  /** The global the library bundle is, whose members are the mapped libraries' namespaces. */
  private[splice] val Libraries: String = "__splice_libs"

  final case class Pin(id: String, url: String, member: String, sha256: String, windows: Boolean)

  def currentPin: Either[SpliceError, Pin] =
    pinFor(sys.props.getOrElse("os.name", ""), sys.props.getOrElse("os.arch", ""))

  def pinFor(osName: String, osArch: String): Either[SpliceError, Pin] =
    val os   = osName.toLowerCase
    val arch = osArch.toLowerCase
    val id   =
      if os.contains("mac") || os.contains("darwin") then
        if arch.contains("aarch64") || arch.contains("arm64") then Some("darwin-arm64")
        else if arch.contains("amd64") || arch.contains("x86_64") then Some("darwin-x64")
        else None
      else if os.contains("linux") then
        if arch.contains("aarch64") || arch.contains("arm64") then Some("linux-arm64")
        else if arch.contains("amd64") || arch.contains("x86_64") then Some("linux-x64")
        else None
      else if os.contains("win") then
        if arch.contains("aarch64") || arch.contains("arm64") then Some("win32-arm64")
        else if arch.contains("amd64") || arch.contains("x86_64") then Some("win32-x64")
        else None
      else None
    id.flatMap(pins.get)
      .toRight(
        SpliceError.Minify(
          s"spliceFull needs a pinned esbuild for this host (got os.name=$osName os.arch=$osArch). Supported: ${pins.keys.toList.sorted.mkString(", ")}"
        )
      )
  end pinFor

  def optimize(js: String, cacheDir: Path, localOnly: Boolean): IO[SpliceError, String] =
    for
      pin     <- ZIO.fromEither(currentPin)
      bin     <- binary(pin, cacheDir, localOnly)
      outcome <- exec(bin, js, List("--minify", "--target=es2015"))
      out     <- outcome match
        case Outcome.Wrote(out)          => ZIO.succeed(out)
        case Outcome.Failed(code, error) => ZIO.fail(SpliceError.Minify(s"esbuild exit $code\n$error"))
    yield out

  /** Bundles `entry` into one script that defines [[Libraries]]. Each bare specifier in `aliases` resolves to its file,
    * so a library that imports another mapped library gets the same module. `process.env.NODE_ENV` reads as `env`.
    */
  def bundle(
      entry: String,
      aliases: Map[String, Path],
      env: NodeEnv,
      cacheDir: Path,
      localOnly: Boolean,
  ): IO[SpliceError, String] =
    val args = List(
      "--bundle",
      "--format=iife",
      s"--global-name=$Libraries",
      "--platform=browser",
      "--log-level=warning",
      "--color=false",
      "--log-override:empty-import-meta=error",
      s"""--define:process.env.NODE_ENV="${env.value}"""",
    ) ++ aliases.toList.sortBy(_._1).map((spec, path) => s"--alias:$spec=${path.toAbsolutePath.normalize}")
    for
      pin     <- ZIO.fromEither(currentPin)
      bin     <- binary(pin, cacheDir, localOnly)
      outcome <- exec(bin, entry, args)
      out     <- outcome match
        case Outcome.Wrote(out)      => ZIO.succeed(out)
        case Outcome.Failed(_, what) => ZIO.fail(bundleError(what))
    yield out
    end for
  end bundle

  /** The first error esbuild reported: an import it could not resolve, or anything else, with the file it named. */
  private[splice] def bundleError(stderr: String): SpliceError =
    reports(stderr).headOption match
      case None                   => SpliceError.Bundle(None, stderr.trim)
      case Some(Report(text, at)) =>
        (text, at) match
          case (couldNotResolve(spec), Some(file)) => SpliceError.Unresolved(spec, file)
          case _                                   => SpliceError.Bundle(at, text)

  /** One `[ERROR]` esbuild printed, and the file its location names. */
  private final case class Report(text: String, file: Option[String])

  private val errorMark: Regex       = """(?m)^(?:✘|X) \[ERROR\] """.r
  private val location: Regex        = """\s+(.+?):\d+:\d+:\s*""".r
  private val couldNotResolve: Regex = """Could not resolve "([^"]+)".*""".r

  private def reports(stderr: String): List[Report] =
    errorMark.split(stderr).toList.drop(1).map { block =>
      val lines = block.linesIterator.toList
      val file  = lines.drop(1).collectFirst { case location(path) => Path.of(path).getFileName.toString }
      Report(lines.headOption.getOrElse("").trim, file)
    }

  /** Hashes what the minifier is given, so a change anywhere a library reaches rebuilds. */
  def programDigest(libraries: String, linker: String, output: Path, sourceMaps: Boolean): String =
    val md                   = MessageDigest.getInstance("SHA-256")
    def add(s: String): Unit =
      md.update(s.getBytes(StandardCharsets.UTF_8))
    add("esbuild-native")
    add(Version)
    add(currentPin.map(_.id).getOrElse("unknown"))
    add(currentPin.map(_.sha256).getOrElse(""))
    add(output.toAbsolutePath.normalize.toString)
    add("maps:" + sourceMaps)
    add(libraries)
    add(linker)
    md.digest.map("%02x".format(_)).mkString
  end programDigest

  private val pins: Map[String, Pin] =
    def tgz(id: String, member: String, sha: String, windows: Boolean) =
      id -> Pin(
        id = id,
        url = s"https://registry.npmjs.org/@esbuild/$id/-/$id-$Version.tgz",
        member = member,
        sha256 = sha,
        windows = windows,
      )
    Map(
      tgz(
        "darwin-arm64",
        "package/bin/esbuild",
        "10b6243df618d374bb2d5c9cfbe7052e1405f6aa4e53a6164f11a91b9f2e1384",
        false,
      ),
      tgz(
        "darwin-x64",
        "package/bin/esbuild",
        "7fe5c9c905fff0a05d92db98929434ab4d3b6dd92d7a7688922db74380c75df3",
        false,
      ),
      tgz(
        "linux-arm64",
        "package/bin/esbuild",
        "90bd269553d258e80b19e3ccab4f98f0831f87442a2f056510ce24fd1b425fc2",
        false,
      ),
      tgz(
        "linux-x64",
        "package/bin/esbuild",
        "e1698a3d5c6c0798fee4fd3b5cc816651f460c63d390a7a26ea4beb0b1884100",
        false,
      ),
      tgz(
        "win32-arm64",
        "package/esbuild.exe",
        "530fb3933af14eefe85dfd06502f826e6ef60fdf4e75228ea67c696db312ecb1",
        true,
      ),
      tgz("win32-x64", "package/esbuild.exe", "c7bee37877d0aa6a046e52783fa0a2cf1a9ce5579d68bb3083bda99d4bff18ef", true),
    )
  end pins

  private def binary(pin: Pin, cacheDir: Path, localOnly: Boolean): IO[SpliceError, Path] =
    val dest = cacheDir.resolve("sbt-splice-esbuild").resolve(Version).resolve(pin.id).resolve(fileName(pin))
    val env  = ResolveEnv(Nil, cacheDir, localOnly, Map.empty, cacheDir)
    for
      hit <- ZIO
        .attemptBlocking(Files.isRegularFile(dest) && Resolve.sha256(dest) == pin.sha256)
        .mapError(e => SpliceError.Io(e.getMessage))
      _ <- ZIO.unless(hit) {
        val tmp = dest.resolveSibling(s"${fileName(pin)}.${Thread.currentThread().threadId}.tmp")
        for
          tgz <- Resolve.fetchCached(pin.url, env)
          _   <- extractMember(tgz, pin.member, tmp)
          _   <- verify(tmp, pin)
          _   <- ZIO.unless(pin.windows)(ZIO.attemptBlocking(chmodX(tmp)).mapError(e => SpliceError.Io(e.getMessage)))
          _   <- ZIO
            .attemptBlocking {
              Files.createDirectories(dest.getParent)
              Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING)
            }
            .mapError(e => SpliceError.Io(e.getMessage))
        yield ()
        end for
      }
      _ <- verify(dest, pin)
    yield dest
    end for
  end binary

  private def fileName(pin: Pin): String = if pin.windows then "esbuild.exe" else "esbuild"

  private def verify(dest: Path, pin: Pin): IO[SpliceError, Unit] =
    val actual = Resolve.sha256(dest)
    if actual == pin.sha256 then ZIO.unit
    else ZIO.fail(SpliceError.ChecksumMismatch(s"esbuild@${pin.id}-$Version", pin.sha256, actual))

  private def extractMember(archive: Path, member: String, dest: Path): IO[SpliceError, Unit] =
    ZIO
      .attemptBlocking {
        Using.resource(
          new TarArchiveInputStream(
            new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(archive)))
          )
        ): tar =>
          var found = false
          var entry = tar.getNextEntry
          while entry != null && !found do
            if entry.getName == member && !entry.isDirectory then
              Files.createDirectories(dest.getParent)
              Files.copy(tar, dest, StandardCopyOption.REPLACE_EXISTING)
              found = true
            entry = tar.getNextEntry
          if !found then Left(SpliceError.MissingArchivePath("esbuild", archive.toString, member))
          else Right(())
      }
      .mapError(e => SpliceError.Io(s"could not read $archive: ${e.getMessage}"))
      .flatMap {
        case Left(err) => ZIO.fail(err)
        case Right(_)  => ZIO.unit
      }

  private def chmodX(path: Path): Unit =
    val perms = Set(
      PosixFilePermission.OWNER_READ,
      PosixFilePermission.OWNER_WRITE,
      PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.GROUP_READ,
      PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_READ,
      PosixFilePermission.OTHERS_EXECUTE,
    ).asJava
    Files.setPosixFilePermissions(path, perms)
    ()
  end chmodX

  /** What one esbuild run left: the file it wrote, or its exit code and what it said. */
  private enum Outcome:
    case Wrote(js: String)
    case Failed(code: Int, stderr: String)

  /** Runs esbuild on `js` with `args`, in a directory of its own that is gone afterwards. */
  private def exec(bin: Path, js: String, args: List[String]): IO[SpliceError, Outcome] =
    ZIO.acquireReleaseWith(
      ZIO.attemptBlocking(Files.createTempDirectory("sbt-splice-esbuild-")).mapError(e => SpliceError.Io(e.getMessage))
    )(dir => ZIO.attemptBlocking(deleteRecursively(dir)).ignore) { dir =>
      ZIO
        .attemptBlockingInterrupt(execIn(bin, dir, js, args))
        .mapError(e => SpliceError.Io(s"esbuild: ${e.getMessage}"))
    }

  private def execIn(bin: Path, dir: Path, js: String, args: List[String]): Outcome =
    val in  = dir.resolve("in.js")
    val out = dir.resolve("out.js")
    Files.writeString(in, js, StandardCharsets.UTF_8)
    val pb = new ProcessBuilder((bin.toAbsolutePath.toString :: "in.js" :: args) :+ "--outfile=out.js"*)
    pb.directory(dir.toFile)
    pb.redirectInput(ProcessBuilder.Redirect.INHERIT)
    val proc = pb.start()
    try
      val err  = String(proc.getErrorStream.readAllBytes(), StandardCharsets.UTF_8)
      val code = proc.waitFor()
      if code != 0 then Outcome.Failed(code, err)
      else if !Files.isRegularFile(out) then Outcome.Failed(code, s"esbuild wrote no out.js\n$err")
      else Outcome.Wrote(Files.readString(out, StandardCharsets.UTF_8))
    finally proc.destroyForcibly()
  end execIn

  private def deleteRecursively(path: Path): Unit =
    if Files.isDirectory(path) then
      val stream = Files.list(path)
      try stream.forEach(p => deleteRecursively(p))
      finally stream.close()
    Files.deleteIfExists(path)
    ()
end EsbuildNative
