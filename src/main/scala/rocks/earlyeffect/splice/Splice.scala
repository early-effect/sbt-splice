package rocks.earlyeffect.splice

import zio.*

/** Effectful splice core. The sbt AutoPlugin is a thin wrapper around this program. */
object Splice:

  def fast: IO[SpliceError, Unit] =
    ZIO.fail(SpliceError.NotImplemented("spliceFast"))

  def full: IO[SpliceError, Unit] =
    ZIO.fail(SpliceError.NotImplemented("spliceFull"))
end Splice
