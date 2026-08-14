package rocks.earlyeffect.splice

import zio.test.*

import java.nio.file.{Files, Path}

object SmokeSiteSpec extends ZIOSpecDefault:

  def spec =
    suite("smoke site")(
      test("writes spliced escape-string-regexp page under target/splice-smoke") {
        SmokeSite.prepare(publishSmoke = true).map { _ =>
          val js = Files.readString(Path.of("target/splice-smoke/fast.js"))
          assertTrue(
            Files.isRegularFile(Path.of("target/splice-smoke/index.html")),
            js.contains("exports.default = function escapeStringRegexp"),
            !js.contains("""from "escape-string-regexp""""),
          )
        }
      }
    )
end SmokeSiteSpec
