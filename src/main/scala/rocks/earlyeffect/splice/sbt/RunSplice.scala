package rocks.earlyeffect.splice.sbt

import rocks.earlyeffect.splice.SpliceError
import _root_.sbt.MessageOnlyException
import zio.*

/** Run a splice `IO` on the sbt thread. Errors become task failures. */
object RunSplice:

  def apply[A](io: IO[SpliceError, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(io) match
        case Exit.Success(a)     => a
        case Exit.Failure(cause) =>
          val msg = cause.failureOption.map(_.message).getOrElse(cause.prettyPrint)
          throw new MessageOnlyException(msg)
    }
end RunSplice
