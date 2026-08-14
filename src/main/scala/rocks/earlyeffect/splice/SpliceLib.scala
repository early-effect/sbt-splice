package rocks.earlyeffect.splice

/** A mapped library: vendor file, CDN coordinate, or WebJar. */
enum SpliceLib derives CanEqual:
  def specifier: String = this match
    case File(s, _)            => s
    case Cdn(s, _, _, _, _)    => s
    case WebJar(s, _, _, _, _) => s

  case File(spec: String, file: java.io.File)
  case Cdn(
      spec: String,
      name: String,
      version: String,
      path: String,
      sha256: Option[String],
  )
  case WebJar(
      spec: String,
      organization: String,
      name: String,
      version: String,
      path: String,
  )
end SpliceLib

object SpliceLib:
  extension (c: Cdn) def sha256(hex: String): Cdn = c.copy(sha256 = Some(hex))
