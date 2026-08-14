package rocks.earlyeffect.splice

import java.nio.file.Path

/** Indexed source maps for prepended wrappers. Offset is exact emitted line count. */
object SourceMaps:

  final case class Section(line: Int, url: String, column: Int = 0)

  /** 0-based line where content after `prefix` begins. Count every `\n`, including blanks. Wrappers we emit end in
    * `\n`, so the next chunk starts at column 0 of that line.
    */
  def lineOffset(prefix: String): Int =
    prefix.count(_ == '\n')

  def indexed(file: String, sections: List[Section]): String =
    val body = sections
      .map { s =>
        s"""{"offset":{"line":${s.line},"column":${s.column}},"url":${jsonString(s.url)}}"""
      }
      .mkString(",")
    s"""{"version":3,"file":${jsonString(file)},"sections":[$body]}"""

  def annotate(js: String, mapFile: String): String =
    val base = if js.endsWith("\n") then js else js + "\n"
    base + s"//# sourceMappingURL=$mapFile\n"

  def relativeUrl(fromMap: Path, toMap: Path): String =
    val from = Option(fromMap.toAbsolutePath.normalize.getParent).getOrElse(fromMap.toAbsolutePath.normalize)
    from.relativize(toMap.toAbsolutePath.normalize).toString.replace('\\', '/')

  def mapPath(js: Path): Path =
    js.resolveSibling(js.getFileName.toString + ".map")

  def mapFileName(js: Path): String =
    js.getFileName.toString + ".map"

  def sectionsFor(
      prefix: String,
      linker: List[LinkerFile],
      fromMap: Path,
  ): List[Section] =
    linker.zipWithIndex.flatMap { (file, i) =>
      file.sourceMap.map { mapPath =>
        val before =
          prefix + linker.take(i).map(_.contents).mkString("\n") + (if i > 0 then "\n" else "")
        Section(lineOffset(before), relativeUrl(fromMap, mapPath))
      }
    }

  private def jsonString(s: String): String =
    val b = new StringBuilder(s.length + 2)
    b.append('"')
    s.foreach {
      case '"'          => b.append("\\\"")
      case '\\'         => b.append("\\\\")
      case c if c < ' ' =>
        b.append(f"\\u${c.toInt}%04x")
      case c => b.append(c)
    }
    b.append('"')
    b.toString
  end jsonString
end SourceMaps
