package rocks.earlyeffect.splice

import java.nio.file.Path

/** One JS file from the Scala.js linker output directory. */
final case class LinkerFile(label: String, contents: String, sourceMap: Option[Path] = None)

/** Inputs to a splice run. `libs` is specifier → vendored file. */
final case class SpliceInput(
    linker: List[LinkerFile],
    libs: Map[String, Path],
    output: Path,
    optimize: Boolean = false,
    extern: Set[String] = Set.empty,
    keepProperties: Set[String] = Set.empty,
    sourceMaps: Boolean = false,
)
