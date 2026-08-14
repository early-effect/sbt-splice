package rocks.earlyeffect.splice.sbt

import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import rocks.earlyeffect.splice.Splice
import sbt.*

/** Post-link splice of pinned JS into Scala.js output. Phase 0: tasks exist and depend on the linker, then fail until
  * Phase 1 implements resolve/splice.
  *
  * Requires ScalaJSPlugin. Enable on a Scala.js project (or rely on `allRequirements` once Scala.js is on the
  * classpath).
  */
object SplicePlugin extends AutoPlugin:

  object autoImport:
    val spliceFast = taskKey[Unit](
      "Splice pinned JS into fastLinkJS output (development). Not implemented until Phase 1."
    )
    val spliceFull = taskKey[Unit](
      "Splice pinned JS into fullLinkJS output and optimize (production). Not implemented until Phase 1."
    )

  import autoImport.*

  override def requires: Plugins      = ScalaJSPlugin
  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[Setting[?]] = Seq(
    spliceFast := Def.uncached {
      (Compile / fastLinkJS).value
      RunSplice(Splice.fast)
    },
    spliceFull := Def.uncached {
      (Compile / fullLinkJS).value
      RunSplice(Splice.full)
    },
  )
end SplicePlugin
