package rocks.earlyeffect.splice

/** A mapped library: vendor file, CDN coordinate, WebJar, or GitHub tag tarball. */
enum SpliceLib derives CanEqual:
  def specifier: String = this match
    case File(s, _, _)            => s
    case Cdn(s, _, _, _, _, _)    => s
    case WebJar(s, _, _, _, _, _) => s
    case GitHub(s, _, _, _, _, _) => s

  def isExtern: Boolean = this match
    case File(_, _, e)            => e
    case Cdn(_, _, _, _, _, e)    => e
    case WebJar(_, _, _, _, _, e) => e
    case GitHub(_, _, _, _, _, e) => e

  /** Closure hatch: wrap and prepend for runtime, do not feed the body to advanced mode. */
  def extern: SpliceLib = this match
    case f: File   => f.copy(asExtern = true)
    case c: Cdn    => c.copy(asExtern = true)
    case w: WebJar => w.copy(asExtern = true)
    case g: GitHub => g.copy(asExtern = true)

  case File(spec: String, file: java.io.File, asExtern: Boolean = false)
  case Cdn(
      spec: String,
      name: String,
      version: String,
      path: String,
      sha256: Option[String],
      asExtern: Boolean = false,
  )
  case WebJar(
      spec: String,
      organization: String,
      name: String,
      version: String,
      path: String,
      asExtern: Boolean = false,
  )
  case GitHub(
      spec: String,
      repository: String,
      tag: String,
      path: String,
      sha256: Option[String],
      asExtern: Boolean = false,
  )
end SpliceLib

object SpliceLib:
  extension (c: Cdn) def sha256(hex: String): Cdn       = c.copy(sha256 = Some(hex))
  extension (g: GitHub) def sha256(hex: String): GitHub = g.copy(sha256 = Some(hex))
