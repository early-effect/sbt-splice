package rocks.earlyeffect.splice.e2e

import chekhov.*
import chekhov.driver.PlaywrightDriver
import chekhov.ziotest.ChekhovSuite
import rocks.earlyeffect.splice.SmokeSite
import zio.*
import zio.test.*

import java.nio.file.Path

/** Firefox smoke against spliced escape-string-regexp. Not aggregated: run `sbt chekhovInstall` then `e2e/testFull`. */
object BrowserSmokeSpec extends ChekhovSuite:

  override def chekhovConfig: ChekhovConfig =
    ChekhovConfig(
      browser = ChekhovBrowser.Firefox,
      headless = true,
      artifactsDir = Path.of("target/chekhov"),
    )

  def spec =
    suite("browser smoke")(
      test("Firefox runs spliced escape-string-regexp and writes the escaped string") {
        SmokeSite.prepare(publishSmoke = false).flatMap { dir =>
          (for
            server <- ZIO.service[AppServer]
            page   <- Chekhov.page
            _      <- page.goto(server.baseUrl + "/")
            text   <- page.innerText("#out")
          yield assertTrue(text == """hello\?""")).provide(
            ZLayer.succeed(chekhovConfig),
            StaticFileServer.layer(dir),
            PlaywrightDriver.suiteLayers,
          )
        }
      }
    )
end BrowserSmokeSpec
