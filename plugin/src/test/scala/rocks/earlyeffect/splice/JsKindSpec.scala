package rocks.earlyeffect.splice

import zio.test.*

object JsKindSpec extends ZIOSpecDefault:

  def spec =
    suite("JsKind")(
      test("classifies ESM, CJS, UMD, and global scripts") {
        val esm = "export function greet() { return 1; }"
        val cjs = "module.exports.greet = function greet() { return 1; };"
        val umd =
          """(function (root, factory) {
            |  if (typeof exports === "object" && typeof module !== "undefined") module.exports = factory();
            |  else root.umd = factory();
            |}(this, function () { return { x: 1 }; }));
            |""".stripMargin
        val global = "(function(){ globalThis.Foo = { x: 1 }; })();"
        assertTrue(
          JsKind.classify(esm) == JsKind.Esm,
          JsKind.classify(cjs) == JsKind.Cjs,
          JsKind.classify(umd) == JsKind.Umd,
          JsKind.classify(global) == JsKind.Global,
          JsKind.classify("function _(n){return n}export{ _ as h};") == JsKind.Esm,
        )
      }
    )
end JsKindSpec
