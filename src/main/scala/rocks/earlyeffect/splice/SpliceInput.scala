package rocks.earlyeffect.splice

import java.nio.file.Path

/** One JS file from the Scala.js linker output directory. */
final case class LinkerFile(label: String, contents: String)

/** Inputs to a splice run. `libs` is specifier → vendored file. */
final case class SpliceInput(
    linker: List[LinkerFile],
    libs: Map[String, Path],
    output: Path,
    optimize: Boolean = false,
)
