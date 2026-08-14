package rocks.earlyeffect.splice

import zio.*

import java.io.File
import java.nio.file.{Files, Path}
import scala.collection.mutable

/** Effectful splice core. The sbt AutoPlugin is a thin wrapper around this program. */
object Splice:

  val maven: SpliceResolver    = SpliceResolver.Maven
  val jsDelivr: SpliceResolver = SpliceResolver.Cdn(
    "jsdelivr",
    (n, v, p) => s"https://cdn.jsdelivr.net/npm/$n@$v/${p.stripPrefix("/")}",
  )
  val unpkg: SpliceResolver = SpliceResolver.Cdn(
    "unpkg",
    (n, v, p) => s"https://unpkg.com/$n@$v/${p.stripPrefix("/")}",
  )

  def cdn(id: String)(expand: (String, String, String) => String): SpliceResolver =
    SpliceResolver.Cdn(id, expand)

  def file(specifier: String, file: File): SpliceLib.File =
    SpliceLib.File(specifier, file)

  def lib(name: String, version: String, path: String): SpliceLib.Cdn =
    SpliceLib.Cdn(name, name, version, path, None)

  def webjar(name: String, version: String, path: String): SpliceLib.WebJar =
    SpliceLib.WebJar(name, "org.webjars.npm", name, version, path)

  def webjar(
      specifier: String,
      organization: String,
      name: String,
      version: String,
      path: String,
  ): SpliceLib.WebJar =
    SpliceLib.WebJar(specifier, organization, name, version, path)

  def resolve(libs: Seq[SpliceLib], env: ResolveEnv): IO[SpliceError, Map[String, Path]] =
    Resolve.files(libs, env)

  def run(input: SpliceInput): IO[SpliceError, Path] =
    for
      _      <- checkLibFiles(input.libs)
      packed <- pack(input)
      spliced = packed.concat
      _    <- leftover(spliced, input.libs.keys, input.output)
      body <-
        if input.optimize then Closure.optimize(packed.inputs)
        else ZIO.succeed(spliced)
      _ <- write(input.output, body)
    yield input.output

  private final case class Packed(inputs: List[(String, String)]):
    def concat: String = inputs.map(_._2).mkString

  private def pack(input: SpliceInput): IO[SpliceError, Packed] =
    ZIO.suspendSucceed {
      val ids    = mutable.Map.empty[String, String]
      val blocks = mutable.ArrayBuffer.empty[(String, String)]

      def wrap(spec: String, path: Path): IO[SpliceError, String] =
        ids.get(spec) match
          case Some(id) => ZIO.succeed(id)
          case None     =>
            if !Files.isRegularFile(path) then ZIO.fail(SpliceError.MissingFile(spec, path.toString))
            else
              val id = JsModules.ident(spec)
              ids.update(spec, id)
              for
                raw <- read(path)
                _   <- unresolvedIn(raw, path.getFileName.toString, input.libs)
                rels = JsModules.specifiers(raw).filter(JsModules.isRelative)
                relMap <- ZIO.foreach(rels) { rel =>
                  val resolved = Option(path.getParent).getOrElse(path).resolve(rel).normalize
                  wrap(resolved.toString, resolved).map(rel -> _)
                }
                modules   = input.libs.keys.map(s => s -> JsModules.ident(s)).toMap ++ relMap.toMap ++ ids.toMap
                rewritten = JsModules.rewriteExports(JsModules.rewrite(raw, modules))
                _ <- ZIO.when(
                  JsModules.leftoverExports(rewritten) ||
                    rewritten.linesIterator.exists(l => l.trim.startsWith("import "))
                )(ZIO.fail(SpliceError.Io(s"could not wrap exports/imports in ${path.getFileName}")))
              yield
                blocks += spec ->
                  s"""const $id = (() => {
                     |  const module = { exports: {} };
                     |  const exports = module.exports;
                     |$rewritten
                     |  return module.exports;
                     |})();
                     |""".stripMargin
                id
              end for

      for
        _ <- ZIO.foreachDiscard(input.linker): file =>
          unresolvedIn(file.contents, file.label, input.libs)
        _ <- ZIO.foreachDiscard(input.libs.toList.sortBy(_._1)): (spec, path) =>
          wrap(spec, path)
        rewritten = input.linker.map(f => JsModules.rewrite(f.contents, ids.toMap)).mkString("\n")
        linkerJs  = if input.optimize then JsModules.dropExports(rewritten) else rewritten
      yield Packed(blocks.toList :+ ("linker.js" -> linkerJs))
    }

  private def checkLibFiles(libs: Map[String, Path]): IO[SpliceError, Unit] =
    ZIO.foreachDiscard(libs.toList): (spec, path) =>
      ZIO.unless(Files.isRegularFile(path))(ZIO.fail(SpliceError.MissingFile(spec, path.toString))).unit

  private def leftover(body: String, mapped: Iterable[String], output: Path): IO[SpliceError, Unit] =
    JsModules.leftoverBare(body, mapped) match
      case Nil       => ZIO.unit
      case spec :: _ => ZIO.fail(SpliceError.LeftoverSpecifier(spec, output.toString))

  private def unresolvedIn(js: String, referring: String, libs: Map[String, Path]): IO[SpliceError, Unit] =
    JsModules.bareRefs(js, referring).find((spec, _) => !libs.contains(spec)) match
      case Some((spec, file)) => ZIO.fail(SpliceError.Unresolved(spec, file))
      case None               => ZIO.unit

  private def read(path: Path): IO[SpliceError, String] =
    ZIO
      .attempt(Files.readString(path))
      .mapError(e => SpliceError.Io(s"could not read $path: ${e.getMessage}"))

  private def write(path: Path, body: String): IO[SpliceError, Unit] =
    ZIO
      .attempt {
        Option(path.getParent).foreach(Files.createDirectories(_))
        Files.writeString(path, body)
        ()
      }
      .mapError(e => SpliceError.Io(s"could not write $path: ${e.getMessage}"))
end Splice
