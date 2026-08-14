package rocks.earlyeffect.splice

import zio.test.*

object SpliceSpec extends ZIOSpecDefault:

  def spec =
    suite("Splice")(
      test("fast is not implemented") {
        Splice.fast.flip.map: err =>
          assertTrue(err == SpliceError.NotImplemented("spliceFast"))
      },
      test("full is not implemented") {
        Splice.full.flip.map: err =>
          assertTrue(err == SpliceError.NotImplemented("spliceFull"))
      },
      test("error message names the task and Phase 1") {
        assertTrue(
          SpliceError.NotImplemented("spliceFast").message
            == "sbt-splice: spliceFast is not implemented yet (Phase 1)"
        )
      },
    )
end SpliceSpec
