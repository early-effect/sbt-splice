package rocks.earlyeffect.splice

/** Failures from the splice program. Values until the sbt plugin boundary. */
enum SpliceError derives CanEqual:
  case Unresolved(specifier: String, referringFile: String)
  case MissingFile(specifier: String, path: String)
  case LeftoverSpecifier(specifier: String, outputFile: String)
  case DuplicateLib(specifier: String)
  case Io(detail: String)

  def message: String = this match
    case Unresolved(specifier, referringFile) =>
      s"""sbt-splice: unresolved specifier "$specifier" in $referringFile"""
    case MissingFile(specifier, path) =>
      s"""sbt-splice: mapped file for "$specifier" is missing: $path"""
    case LeftoverSpecifier(specifier, outputFile) =>
      s"""sbt-splice: leftover specifier "$specifier" in $outputFile"""
    case DuplicateLib(specifier) =>
      s"""sbt-splice: duplicate spliceLibs entry for "$specifier""""
    case Io(detail) =>
      s"sbt-splice: $detail"
end SpliceError
