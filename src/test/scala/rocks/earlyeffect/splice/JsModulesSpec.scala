package rocks.earlyeffect.splice

import zio.test.*

object JsModulesSpec extends ZIOSpecDefault:

  def spec =
    suite("JsModules")(
      test("treats package names as bare and relative paths as not") {
        assertTrue(
          JsModules.isBare("foo"),
          JsModules.isBare("escape-string-regexp"),
          JsModules.isBare("foo/plugin"),
          !JsModules.isBare("./util.js"),
          !JsModules.isBare("../x.js"),
          !JsModules.isBare("/abs.js"),
          !JsModules.isBare("https://example.com/x.js"),
          JsModules.isRelative("./util.js"),
          !JsModules.isRelative("foo"),
        )
      },
      test("rewrites Scala.js namespace, default, named, and require forms") {
        val modules = Map("foo" -> "__splice_foo")
        val ns      = JsModules.rewrite("""import * as $i_foo from "foo";""", modules)
        val dflt    = JsModules.rewrite("""import foo from "foo";""", modules)
        val named   = JsModules.rewrite("""import { greet as $g } from "foo";""", modules)
        val req     = JsModules.rewrite("""const x = require("foo");""", modules)
        assertTrue(
          ns == "const $i_foo = __splice_foo;",
          dflt == "const foo = __splice_foo.default;",
          named == "const $g = __splice_foo.greet;",
          req == "const x = __splice_foo;",
        )
      },
      test("rewrites export default and export function into exports assignments") {
        val body =
          """export default function escapeStringRegexp(string) { return string; }
            |export function greet() { return "ok"; }
            |""".stripMargin
        val out = JsModules.rewriteExports(body)
        assertTrue(
          out.contains("exports.default = function escapeStringRegexp"),
          out.contains("exports.greet = function greet"),
          !out.contains("export "),
        )
      },
    )
end JsModulesSpec
