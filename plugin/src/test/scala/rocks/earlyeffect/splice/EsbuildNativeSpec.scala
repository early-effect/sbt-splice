package rocks.earlyeffect.splice

import zio.test.*
import zio.*

import java.nio.file.Files

object EsbuildNativeSpec extends ZIOSpecDefault:

  def spec = suite("EsbuildNative")(
    test("this host has a pinned esbuild") {
      assertTrue(EsbuildNative.currentPin.isRight)
    },
    test("minify keeps class extends Error and super, not Error.call") {
      for
        js <- EsbuildNative.optimize(
          ProtocolFixtures.errorSubclass,
          FileCacheDir,
          localOnly = false,
        )
        out = JsHost.evalExpr(js, "document.getElementById('out').textContent")
      yield assertTrue(
        out == ProtocolFixtures.errorSubclassOut,
        js.contains("extends Error"),
        !js.contains("Error.call("),
        !js.contains("this.message="),
        !js.contains("this.message ="),
      )
    },
    test("minify keeps a class-extends render override") {
      val src = ProtocolFixtures.classComponentVendor + ProtocolFixtures.scalaJsClassSubclass
      for
        js <- EsbuildNative.optimize(src, FileCacheDir, localOnly = false)
        out = JsHost.evalExpr(js, "document.getElementById('out').textContent")
      yield assertTrue(
        out == ProtocolFixtures.classComponentOut,
        js.contains("setState"),
        js.contains("render"),
      )
    },
    test("minify is smaller than concat") {
      val src = ProtocolFixtures.classComponentVendor + ProtocolFixtures.hCallLinker
      for js <- EsbuildNative.optimize(src, FileCacheDir, localOnly = false)
      yield assertTrue(js.length < src.length)
    },
    test("spliceFull minify path keeps Error subclass") {
      for
        dir <- tempDir
        out = dir.resolve("full.js")
        _ <- Splice.run(
          SpliceInput(
            linker = List(LinkerFile("main.js", ProtocolFixtures.errorSubclass)),
            libs = Map.empty,
            output = out,
            minify = Minify.Esbuild,
          )
        )
        body <- ZIO.attempt(Files.readString(out))
        got = JsHost.evalExpr(body, "document.getElementById('out').textContent")
      yield assertTrue(
        got == ProtocolFixtures.errorSubclassOut,
        !body.contains("Error.call("),
      )
    },
    test("unknown host fails without fetching") {
      val err = EsbuildNative.pinFor("SerenityOS", "riscv64")
      assertTrue(
        err.isLeft,
        err.swap.exists(_.message.contains("darwin-arm64")),
      )
    },
  )

  private val FileCacheDir = coursier.cache.FileCache().location.toPath

  private def tempDir: Task[java.nio.file.Path] =
    ZIO.attempt(Files.createTempDirectory("sbt-splice-esbuild-spec-"))
end EsbuildNativeSpec
