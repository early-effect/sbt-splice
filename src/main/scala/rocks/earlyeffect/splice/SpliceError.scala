package rocks.earlyeffect.splice

/** Failures from the splice program. Values until the sbt plugin boundary. */
enum SpliceError derives CanEqual:
  case NotImplemented(taskName: String)

  def message: String = this match
    case NotImplemented(taskName) =>
      s"sbt-splice: $taskName is not implemented yet (Phase 1)"
end SpliceError
