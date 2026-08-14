package rocks.earlyeffect.splice

import java.io.File

/** One mapped library: the bare specifier `@JSImport` uses, and the JS file on disk. */
final case class SpliceLib(specifier: String, file: File)
