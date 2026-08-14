package rocks.earlyeffect.splice

import java.util.regex.Matcher
import scala.util.matching.Regex

/** Find and rewrite bare / relative module specifiers. No JS parser: Scala.js import lines are regular. */
object JsModules:

  private val fromPat: Regex    = """from\s+(['"])([^'"]+)\1""".r
  private val requirePat: Regex = """require\s*\(\s*(['"])([^'"]+)\1\s*\)""".r
  // Bindings may be `$i_foo` (Scala.js). `\S+` avoids a `$` in the regex source.
  private val nsImport: Regex =
    """(?m)^import\s+\*\s+as\s+(\S+)\s+from\s+(['"])([^'"]+)\2\s*;?\s*$""".r
  private val defaultImport: Regex =
    """(?m)^import\s+(\S+)\s+from\s+(['"])([^'"]+)\2\s*;?\s*$""".r
  private val namedImport: Regex =
    """(?m)^import\s+\{([^}]+)\}\s+from\s+(['"])([^'"]+)\2\s*;?\s*$""".r

  def isBare(specifier: String): Boolean =
    specifier.nonEmpty &&
      !specifier.startsWith(".") &&
      !specifier.startsWith("/") &&
      !specifier.contains(":")

  def isRelative(specifier: String): Boolean =
    specifier.startsWith("./") || specifier.startsWith("../")

  def ident(specifier: String): String =
    "__splice_" + specifier.replaceAll("[^A-Za-z0-9]", "_")

  /** Bare specifier references in `js`, attributed to `referring`. */
  def bareRefs(js: String, referring: String): List[(String, String)] =
    specifiers(js).distinct.filter(isBare).map(_ -> referring)

  def specifiers(js: String): List[String] =
    fromPat.findAllMatchIn(js).map(_.group(2)).toList ++
      requirePat.findAllMatchIn(js).map(_.group(2)).toList

  def rewrite(js: String, modules: Map[String, String]): String =
    def q(s: String): String            = Matcher.quoteReplacement(s)
    def namedOf(m: Regex.Match): String =
      modules.get(m.group(3)) match
        case None     => q(m.matched)
        case Some(id) =>
          q(
            m.group(1)
              .split(",")
              .toList
              .map(_.trim)
              .filter(_.nonEmpty)
              .map { binding =>
                val parts = binding.split("\\s+as\\s+")
                if parts.length == 2 then s"const ${parts(1).trim} = $id.${parts(0).trim};"
                else s"const $binding = $id.$binding;"
              }
              .mkString(" ")
          )
    def nsOf(m: Regex.Match): String =
      modules.get(m.group(3)) match
        case Some(id) => q(s"const ${m.group(1)} = $id;")
        case None     => q(m.matched)
    def defaultOf(m: Regex.Match): String =
      modules.get(m.group(3)) match
        case Some(id) => q(s"const ${m.group(1)} = $id.default;")
        case None     => q(m.matched)
    def requireOf(m: Regex.Match): String =
      modules.get(m.group(2)) match
        case Some(id) => q(id)
        case None     => q(m.matched)
    val named       = namedImport.replaceAllIn(js, namedOf)
    val withNs      = nsImport.replaceAllIn(named, nsOf)
    val withDefault = defaultImport.replaceAllIn(withNs, defaultOf)
    requirePat.replaceAllIn(withDefault, requireOf)
  end rewrite

  def leftoverBare(js: String, mapped: Iterable[String]): List[String] =
    val found = specifiers(js).toSet
    mapped.toList.filter(spec => isBare(spec) && found.contains(spec))

  def rewriteExports(body: String): String =
    body
      .replaceAll("""export\s+default\s+""", "exports.default = ")
      .replaceAll("""export\s+function\s+(\w+)""", "exports.$1 = function $1")
      .replaceAll("""export\s+class\s+(\w+)""", "exports.$1 = class $1")
      .replaceAll("""export\s+(?:const|let|var)\s+(\w+)\s*=""", "exports.$1 =")

  /** Drop ES module export lines so Closure can compile a script. */
  def dropExports(js: String): String =
    js.linesIterator
      .filterNot { line =>
        val t = line.trim
        t.startsWith("export ") || t.startsWith("export{") || t.startsWith("export*")
      }
      .mkString("\n")
end JsModules
