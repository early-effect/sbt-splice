package rocks.earlyeffect.splice.zipx

import rocks.earlyeffect.splice.SplicePlugin
import rocks.earlyeffect.splice.SplicePlugin.autoImport.spliceLibs
import sbt.*
import sbt.Keys.*
import zipx.plugin.ZipxPlugin
import zipx.plugin.ZipxPlugin.autoImport.*

/** Opt-in zipx integration: emit splice plugin lines and register a pin feed from `spliceLibs`.
  *
  * Enable by adding this plugin. It requires both sbt-splice and sbt-zipx; `allRequirements` turns it on when those are
  * already on the classpath. sbt-splice itself does not depend on zipx.
  */
object SpliceZipxPlugin extends AutoPlugin:

  object autoImport:
    val splicePinLookup = settingKey[zipx.core.PinLookup](
      "Lookup used by the splice pin feed. Default talks to the jsDelivr data API and GitHub tags."
    )
    val splicePinResolveSha = settingKey[SplicePins.ResolveSha](
      "Fetch the new CDN file or GitHub tarball and return its sha256. Version and hash move together."
    )
    export rocks.earlyeffect.splice.zipx.SplicePins
  end autoImport

  import autoImport.*

  override def requires: Plugins      = SplicePlugin && ZipxPlugin
  override def trigger: PluginTrigger = allRequirements

  override def buildSettings: Seq[Setting[?]] = Seq(
    zipxSelfPlugins += ZipxSelf.emit("rocks.earlyeffect", "sbt-splice", SplicePlugin.getClass),
    zipxSelfPlugins += ZipxSelf.emit("rocks.earlyeffect", "sbt-splice-zipx", getClass),
    ThisBuild / splicePinLookup     := Def.uncached(SplicePins.lookupDefault),
    ThisBuild / splicePinResolveSha := Def.uncached {
      SplicePins.resolveShaDefault((LocalRootProject / csrCacheDirectory).value.toPath)
    },
    ThisBuild / zipxPinFeeds ++= Def.uncached {
      val libs = spliceLibs.?.all(ScopeFilter(inAnyProject)).value.flatten.flatten.toList
      val base = (LocalRootProject / baseDirectory).value
      SplicePins.feed(
        libs,
        base,
        (ThisBuild / splicePinLookup).value,
        (ThisBuild / splicePinResolveSha).value,
      )
    },
  )
end SpliceZipxPlugin
