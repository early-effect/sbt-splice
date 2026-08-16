package rocks.earlyeffect.splice.zipx

import coursier.cache.{CachePolicy, FileCache}
import coursier.util.Artifact
import rocks.earlyeffect.splice.{Resolve, Splice, SpliceLib, SpliceResolver}
import zipx.core.*

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.Duration
import scala.concurrent.ExecutionContext

/** Inventory, lookup, and apply for the splice pin feed. zipx owns Ignore/Report/Update; this object owns JS pins. */
object SplicePins:

  val FeedName: PinFeedName = PinFeedName("splice")

  type ResolveSha = (SpliceLib, String) => Either[String, String]

  def constLookup(to: String): PinLookup = _ => Right(Some(to))
  def constSha(hex: String): ResolveSha  = (_, _) => Right(hex)

  /** npm when both sides parse as semver, otherwise exact (GitHub tags that are not x.y.z). */
  val classify: VersionStrategy = new VersionStrategy:
    def classify(current: String, candidate: String): BumpKind =
      val npm = VersionStrategy.npm.classify(current, candidate)
      if npm != BumpKind.None then npm else VersionStrategy.exact.classify(current, candidate)
    def latestStable(candidates: List[String]): Option[String] =
      VersionStrategy.npm.latestStable(candidates).orElse(VersionStrategy.exact.latestStable(candidates))

  def inventory(libs: Seq[SpliceLib]): List[PinnedDep] =
    libs.flatMap(toPin).distinctBy(_.id).toList

  def feed(
      libs: Seq[SpliceLib],
      base: java.io.File,
      lookup: PinLookup,
      resolveSha: ResolveSha,
  ): Seq[PinFeed] =
    val pins    = inventory(libs)
    val webjars = libs.collect { case w: SpliceLib.WebJar => w.specifier }.toSet
    if pins.isEmpty then Nil
    else
      List(
        PinFeed(
          name = FeedName,
          inventory = pins,
          classify = classify,
          lookup = pin => if webjars.contains(pin.id) then Right(None) else lookup(pin),
          apply = applyPin(libs, base, webjars, resolveSha),
        )
      )
    end if
  end feed

  def lookupDefault: PinLookup = pin =>
    pin.purl.map(p => p: String) match
      case Some(s) if s.startsWith("pkg:npm/")    => lookupNpm(npmName(s))
      case Some(s) if s.startsWith("pkg:github/") => lookupGithub(githubRepo(s))
      case _                                      => Right(None)

  def resolveShaDefault(cacheDir: Path): ResolveSha = (lib, to) =>
    lib match
      case c: SpliceLib.Cdn =>
        coursierFile(cdnUrl(c.name, to, c.path), cacheDir).map(Resolve.sha256)
      case g: SpliceLib.GitHub =>
        githubArchive(g.repository, to).flatMap(url => coursierFile(url, cacheDir).map(Resolve.sha256))
      case other =>
        Left(s"splice pin apply: ${other.specifier} has no sha256 to rewrite")

  def rewrite(
      base: java.io.File,
      oldVersion: String,
      newVersion: String,
      oldSha: Option[String],
      newSha: String,
  ): Either[String, Unit] =
    oldSha.map(_.trim.toLowerCase).filter(_.nonEmpty) match
      case None          => Left("splice pin apply: missing sha256; version and hash must move together")
      case Some(fromSha) =>
        val files = sourceFiles(base)
        val hits  = files.flatMap { f =>
          val text = Files.readString(f.toPath)
          val n    = count(text, fromSha)
          if n == 0 then None else Some((f, text, n))
        }
        val total = hits.map(_._3).sum
        if total == 0 then Left(s"splice pin apply: sha256 not found under ${base.getPath}")
        else if total != 1 then Left(s"splice pin apply: sha256 is not unique ($total occurrences)")
        else
          val (f, text, _) = hits.head
          val fromVer      = s"\"$oldVersion\""
          val toVer        = s"\"$newVersion\""
          val versions     = count(text, fromVer)
          if versions == 0 then Left(s"splice pin apply: $fromVer not found next to the sha256 in ${f.getName}")
          else if versions != 1 then
            Left(s"splice pin apply: $fromVer is not unique in ${f.getName} ($versions occurrences)")
          else
            Files.writeString(f.toPath, text.replace(fromVer, toVer).replace(fromSha, newSha.toLowerCase))
            Right(())
        end if

  private def applyPin(
      libs: Seq[SpliceLib],
      base: java.io.File,
      webjars: Set[String],
      resolveSha: ResolveSha,
  ): PinApply = (pin, to) =>
    if webjars.contains(pin.id) then Right(())
    else
      libs.find(_.specifier == pin.id) match
        case None                    => Left(s"splice pin '${pin.id}' is not in spliceLibs")
        case Some(_: SpliceLib.File) => Right(())
        case Some(c: SpliceLib.Cdn)  =>
          resolveSha(c, to).flatMap(sha => rewrite(base, c.version, to, c.sha256, sha))
        case Some(g: SpliceLib.GitHub) =>
          resolveSha(g, to).flatMap(sha => rewrite(base, g.tag, to, g.sha256, sha))
        case Some(_: SpliceLib.WebJar) => Right(())

  private def toPin(lib: SpliceLib): Option[PinnedDep] =
    lib match
      case _: SpliceLib.File => None
      case c: SpliceLib.Cdn  =>
        Some(PinnedDep(c.specifier, c.version, npmPurl(c.name, c.version)))
      case w: SpliceLib.WebJar =>
        Some(PinnedDep(w.specifier, w.version, npmPurl(w.name, w.version)))
      case g: SpliceLib.GitHub =>
        Some(PinnedDep(g.specifier, g.tag, githubPurl(g.repository, g.tag)))

  private def npmPurl(name: String, version: String): Option[Purl] =
    val encoded = if name.startsWith("@") then s"%40${name.drop(1)}" else name
    Purl.make(s"pkg:npm/$encoded@$version").toOption

  private def githubPurl(repository: String, tag: String): Option[Purl] =
    Purl.make(s"pkg:github/$repository@$tag").toOption

  private def npmName(purl: String): String =
    val rest = purl.stripPrefix("pkg:npm/")
    val cut  = rest.lastIndexOf('@')
    decodePurl(if cut < 0 then rest else rest.substring(0, cut))

  private def githubRepo(purl: String): String =
    val rest = purl.stripPrefix("pkg:github/")
    val cut  = rest.lastIndexOf('@')
    if cut < 0 then rest else rest.substring(0, cut)

  private def decodePurl(s: String): String =
    if s.startsWith("%40") then s"@${s.drop(3)}" else s

  private def lookupNpm(name: String): Either[String, Option[String]] =
    val encoded = java.net.URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20")
    httpGet(s"https://data.jsdelivr.com/v1/packages/npm/$encoded").map(jsDelivrLatest)

  private def lookupGithub(repository: String): Either[String, Option[String]] =
    httpGet(s"https://api.github.com/repos/$repository/tags?per_page=100").map { json =>
      val names = quotedValues(json, "name")
      classify.latestStable(names).orElse(names.headOption)
    }

  private def jsDelivrLatest(json: String): Option[String] =
    val latest = """"tags"\s*:\s*\{[^}]*"latest"\s*:\s*"([^"]+)"""".r.findFirstMatchIn(json).map(_.group(1))
    latest.orElse(classify.latestStable(quotedValues(json, "version")))

  private def quotedValues(json: String, key: String): List[String] =
    s""""$key"\\s*:\\s*"([^"]+)"""".r.findAllMatchIn(json).map(_.group(1)).toList

  private def httpGet(url: String): Either[String, String] =
    try
      val req = HttpRequest
        .newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("User-Agent", "sbt-splice-zipx")
        .header("Accept", "application/json")
      val authed =
        if url.contains("api.github.com") then
          sys.env.get("GITHUB_TOKEN").filter(_.nonEmpty) match
            case Some(token) => req.header("Authorization", s"Bearer $token")
            case None        => req
        else req
      val resp = client.send(authed.GET().build(), HttpResponse.BodyHandlers.ofString())
      if resp.statusCode() == 200 then Right(resp.body())
      else Left(s"splice pin lookup: HTTP ${resp.statusCode()} $url")
    catch case e: Throwable => Left(s"splice pin lookup: ${e.getMessage}")

  private lazy val client: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()

  private def cdnUrl(name: String, version: String, path: String): String =
    Splice.jsDelivr match
      case SpliceResolver.Cdn(_, expand) => expand(name, version, path)
      case _                             => s"https://cdn.jsdelivr.net/npm/$name@$version/${path.stripPrefix("/")}"

  private def githubArchive(repository: String, tag: String): Either[String, String] =
    repository.split("/", -1) match
      case Array(owner, repo) if owner.nonEmpty && repo.nonEmpty =>
        Splice.github match
          case SpliceResolver.GitHub(expand) => Right(expand(owner, repo, tag))
          case _                             => Right(s"https://github.com/$owner/$repo/archive/refs/tags/$tag.tar.gz")
      case _ => Left(s"""GitHub repository must be owner/repo, got "$repository"""")

  private def coursierFile(url: String, cacheDir: Path): Either[String, Path] =
    try
      given ExecutionContext = ExecutionContext.global
      val cache              = FileCache()
        .withLocation(cacheDir.toFile)
        .withChecksums(Seq(None))
        .withCachePolicies(Seq(CachePolicy.FetchMissing))
      cache.file(Artifact(url)).run.unsafeRun() match
        case Left(err) => Left(s"splice pin apply: ${err.describe} ($url)")
        case Right(f)  => Right(f.toPath)
    catch case e: Throwable => Left(s"splice pin apply: ${e.getMessage}")

  private def sourceFiles(base: java.io.File): List[java.io.File] =
    def go(dir: java.io.File): List[java.io.File] =
      Option(dir.listFiles).toList.flatten.flatMap { f =>
        val n = f.getName
        if n == "target" || n.startsWith(".") then Nil
        else if f.isDirectory then go(f)
        else if n.endsWith(".sbt") || n.endsWith(".scala") then List(f)
        else Nil
      }
    go(base)
  end sourceFiles

  private def count(hay: String, needle: String): Int =
    if needle.isEmpty then 0
    else
      var n = 0
      var i = 0
      while i >= 0 do
        val j = hay.indexOf(needle, i)
        if j < 0 then i = -1
        else
          n += 1
          i = j + needle.length
      n
end SplicePins
