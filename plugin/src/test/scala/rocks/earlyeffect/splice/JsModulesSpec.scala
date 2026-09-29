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
      test("finds each import statement's specifier, and the specifier a re-export names") {
        val statements = Gen.elements(
          "import * as $i_foo from ",
          "import foo from ",
          "import { greet as $g } from ",
          "import {\n  a,\n  b as c,\n} from ",
          "import d, { e } from ",
          "import ",
          "export * from ",
          "export { x as y } from ",
        )
        val before  = Gen.elements("", "const q = 1;\n", "/* head */\n", "  ")
        val quotes  = Gen.elements("\"", "'")
        val modules = Gen.elements("foo", "@scope/pkg", "./util.js", "../x/y.js", "pkg/sub")
        check(before, statements, quotes, modules) { (lead, statement, quote, module) =>
          val js = s"$lead$statement$quote$module$quote;\nconst z = 2;"
          assertTrue(JsModules.specifiers(js) == List(module))
        }
      },
      test("a string that says import or from is not an import") {
        check(Gen.alphaNumericStringBounded(0, 12)) { word =>
          val js =
            s"""throw new Error(("Unable to obtain LocalDate from " + $$m_x().y(a_$$_lo)) + "$word");
               |const msg = "$word from " + v;
               |const s = 'import x from ' + "y";""".stripMargin
          assertTrue(JsModules.specifiers(js).isEmpty)
        }
      },
      test("rewrites a side-effect import to nothing, since the module already ran where it was spliced") {
        val modules = Map("foo" -> "__splice_foo")
        assertTrue(
          JsModules.rewrite("import \"foo\";\nconst x = 1;", modules) == "\nconst x = 1;",
          JsModules.rewrite("import 'bar';", modules) == "import 'bar';",
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
      test("rewrites inline export lists used by published ESM bundles") {
        val body = "function _(n){return n}function x(){}export{x as Component,_ as h};"
        val out  = JsModules.rewriteExports(body)
        assertTrue(
          out.contains("exports.Component = x;"),
          out.contains("exports.h = _;"),
          !JsModules.leftoverExports(out),
        )
      },
      test("dropExports removes ES module export lines") {
        val js =
          """const x = 1;
            |export { Hello as Hello };
            |export{Foo};
            |""".stripMargin
        val out = JsModules.dropExports(js)
        assertTrue(
          out.contains("const x = 1;"),
          !out.contains("export"),
        )
      },
    )
end JsModulesSpec
