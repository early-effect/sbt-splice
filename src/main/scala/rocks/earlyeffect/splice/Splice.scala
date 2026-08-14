package rocks.earlyeffect.splice

import zio.*

import java.io.File
import java.nio.file.{Files, Path}
import scala.collection.mutable

/** Effectful splice core. The sbt AutoPlugin is a thin wrapper around this program. */
object Splice:

  def file(specifier: String, file: File): SpliceLib =
    SpliceLib(specifier, file)

  def run(input: SpliceInput): IO[SpliceError, Path] =
    for
      _      <- checkLibFiles(input.libs)
      packed <- pack(input)
      body = packed.blocks.mkString + packed.linkerJs
      _ <- leftover(body, input.libs.keys, input.output)
      _ <- write(input.output, body)
    yield input.output

  private final case class Packed(blocks: List[String], linkerJs: String)

  private def pack(input: SpliceInput): IO[SpliceError, Packed] =
    ZIO.suspendSucceed {
      val ids    = mutable.Map.empty[String, String]
      val blocks = mutable.ArrayBuffer.empty[String]

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
                  rewritten.contains("export ") ||
                    rewritten.linesIterator.exists(l => l.trim.startsWith("import "))
                )(ZIO.fail(SpliceError.Io(s"could not wrap exports/imports in ${path.getFileName}")))
              yield
                blocks +=
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
        linkerJs = input.linker.map(f => JsModules.rewrite(f.contents, ids.toMap)).mkString("\n")
      yield Packed(blocks.toList, linkerJs)
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
