import org.graalvm.polyglot.{Context, HostAccess, Value}

/** Eval spliced JS in GraalJS. Scripted meta-build only; not the plugin. */
object JsHost:

  private val documentShim =
    """var document = {
      |  getElementById: function(id) {
      |    this._n = this._n || {};
      |    this._n[id] = this._n[id] || { textContent: "" };
      |    return this._n[id];
      |  }
      |};
      |""".stripMargin

  def evalExpr(js: String, expr: String): String =
    val ctx = Context
      .newBuilder("js")
      .option("engine.WarnInterpreterOnly", "false")
      .allowHostAccess(HostAccess.NONE)
      .build()
    try
      ctx.eval("js", documentShim + asScript(js))
      stringify(ctx.eval("js", expr), expr)
    finally ctx.close()
  end evalExpr

  private def asScript(js: String): String =
    val noExportLines = js.linesIterator
      .filterNot { line =>
        val t = line.trim
        t.startsWith("export ") || t.startsWith("export{") || t.startsWith("export*")
      }
      .mkString("\n")
    raw"export\s*\{[^}]*\}\s*;?".r.replaceAllIn(noExportLines, "")

  private def stringify(value: Value, label: String): String =
    if value == null || value.isNull then sys.error(s"missing JS value: $label")
    else if value.isString then value.asString
    else value.toString
end JsHost
