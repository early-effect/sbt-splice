package rocks.earlyeffect.splice

/** Published JS module shape. Classifier is regex, not a parser. */
enum JsKind derives CanEqual:
  case Esm, Cjs, Umd, Global

object JsKind:

  def classify(js: String): JsKind =
    if isEsm(js) then JsKind.Esm
    else if isUmd(js) then JsKind.Umd
    else if isCjs(js) then JsKind.Cjs
    else JsKind.Global

  private def isEsm(js: String): Boolean =
    js.contains("export{") ||
      js.contains("export {") ||
      js.contains("export*") ||
      js.contains("export *") ||
      """(?m)^\s*export\s""".r.findFirstIn(js).isDefined

  private def isUmd(js: String): Boolean =
    js.contains("typeof exports") &&
      (js.contains("typeof module") || js.contains("typeof define"))

  private def isCjs(js: String): Boolean =
    js.contains("module.exports") ||
      """(?m)^\s*exports\.""".r.findFirstIn(js).isDefined
end JsKind
