package rocks.earlyeffect.splice

import zio.*
import zio.test.*

import com.sun.net.httpserver.{HttpExchange, HttpServer}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.jar.{JarEntry, JarOutputStream}

object ResolveSpec extends ZIOSpecDefault:

  import SpliceLib.sha256

  def spec =
    suite("Resolve")(
      test("vendor file maps to the path on disk") {
        for
          dir <- tempDir
          foo = dir.resolve("foo.js")
          _ <- write(foo, "export const x = 1;")
          env = envAt(dir)
          got <- Splice.resolve(Seq(Splice.file("foo", foo.toFile)), env)
        yield assertTrue(got("foo") == foo)
      },
      test("duplicate specifiers fail") {
        for
          dir <- tempDir
          foo = dir.resolve("foo.js")
          _   <- write(foo, "export const x = 1;")
          err <- Splice
            .resolve(
              Seq(Splice.file("foo", foo.toFile), Splice.file("foo", foo.toFile)),
              envAt(dir),
            )
            .flip
        yield assertTrue(err == SpliceError.DuplicateLib("foo"))
      },
      test("CDN without sha256 fails before fetch") {
        for
          dir <- tempDir
          err <- Splice
            .resolve(
              Seq(Splice.lib("foo", "1.0.0", "foo.js")),
              envAt(dir).copy(resolvers = Seq(Splice.jsDelivr)),
            )
            .flip
        yield assertTrue(err == SpliceError.MissingSha256("foo"))
      },
      test("CDN with no CDN resolver fails") {
        for
          dir <- tempDir
          err <- Splice
            .resolve(
              Seq(Splice.lib("foo", "1.0.0", "foo.js").sha256("abcd")),
              envAt(dir).copy(resolvers = Seq(Splice.maven)),
            )
            .flip
        yield assertTrue(err == SpliceError.NoResolver("foo", "cdn"))
      },
      test("jsDelivr-shaped HTTP fetch verifies sha256") {
        val body = """export function greet() { return "ok"; }"""
        val pin  = hex(body.getBytes(StandardCharsets.UTF_8))
        ZIO.scoped {
          for
            port <- serve(body)
            dir  <- tempDir
            env = envAt(dir).copy(resolvers = Seq(localCdn(port)))
            got <- Splice.resolve(
              Seq(Splice.lib("foo", "1.0.0", "foo.js").sha256(pin)),
              env,
            )
            bytes <- ZIO.attempt(Files.readAllBytes(got("foo")))
          yield assertTrue(
            got.contains("foo"),
            String(bytes, StandardCharsets.UTF_8) == body,
          )
        }
      },
      test("wrong sha256 fails and does not fall across CDNs") {
        val body = """export function greet() { return "ok"; }"""
        ZIO.scoped {
          for
            port <- serve(body)
            dir  <- tempDir
            env = envAt(dir).copy(resolvers = Seq(localCdn(port), Splice.unpkg))
            err <- Splice
              .resolve(
                Seq(Splice.lib("foo", "1.0.0", "foo.js").sha256("0" * 64)),
                env,
              )
              .flip
          yield assertTrue(
            err.isInstanceOf[SpliceError.ChecksumMismatch],
            err.asInstanceOf[SpliceError.ChecksumMismatch].specifier == "foo",
          )
        }
      },
      test("cache hit does not need the server (LocalOnly)") {
        val body = """export function greet() { return "ok"; }"""
        val pin  = hex(body.getBytes(StandardCharsets.UTF_8))
        for
          dir  <- tempDir
          port <- Ref.make(0)
          _    <- ZIO.scoped {
            for
              p <- serve(body)
              _ <- port.set(p)
              env = envAt(dir).copy(resolvers = Seq(localCdn(p)))
              _ <- Splice.resolve(Seq(Splice.lib("foo", "1.0.0", "foo.js").sha256(pin)), env)
            yield ()
          }
          p <- port.get
          offline = envAt(dir).copy(resolvers = Seq(localCdn(p)), localOnly = true)
          got <- Splice.resolve(Seq(Splice.lib("foo", "1.0.0", "foo.js").sha256(pin)), offline)
        yield assertTrue(got.contains("foo"), Files.isRegularFile(got("foo")))
        end for
      },
      test("WebJar extract reads META-INF/resources/webjars") {
        for
          dir <- tempDir
          jar <- jarWith(dir, "foo", "1.0.0", "foo.js", """export function greet() { return "ok"; }""")
          env = envAt(dir).copy(
            resolvers = Seq(Splice.maven),
            webjars = Map("foo" -> jar),
          )
          got  <- Splice.resolve(Seq(Splice.webjar("foo", "1.0.0", "foo.js")), env)
          text <- ZIO.attempt(Files.readString(got("foo")))
        yield assertTrue(text.contains("greet"))
      },
      test("WebJar missing path fails") {
        for
          dir <- tempDir
          jar <- jarWith(dir, "foo", "1.0.0", "other.js", "export const x = 1;")
          env = envAt(dir).copy(
            resolvers = Seq(Splice.maven),
            webjars = Map("foo" -> jar),
          )
          err <- Splice.resolve(Seq(Splice.webjar("foo", "1.0.0", "foo.js")), env).flip
        yield assertTrue(err.isInstanceOf[SpliceError.MissingJarPath])
      },
      test("WebJar without maven resolver fails") {
        for
          dir <- tempDir
          err <- Splice
            .resolve(
              Seq(Splice.webjar("foo", "1.0.0", "foo.js")),
              envAt(dir).copy(resolvers = Seq.empty),
            )
            .flip
        yield assertTrue(err == SpliceError.NoResolver("foo", "maven"))
      },
    )

  private def localCdn(port: Int): SpliceResolver =
    Splice.cdn("local") { (n, v, p) =>
      s"http://127.0.0.1:$port/npm/$n@$v/$p"
    }

  private def envAt(dir: Path): ResolveEnv =
    ResolveEnv(
      resolvers = Seq(Splice.maven),
      cacheDir = dir.resolve("csr"),
      localOnly = false,
      webjars = Map.empty,
      extractDir = dir.resolve("extracted"),
    )

  private def serve(body: String): ZIO[Scope, Throwable, Int] =
    val bytes = body.getBytes(StandardCharsets.UTF_8)
    ZIO
      .acquireRelease(ZIO.attempt {
        val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(
          "/",
          (ex: HttpExchange) =>
            try
              if ex.getRequestURI.getPath.endsWith("/foo.js") then
                ex.sendResponseHeaders(200, bytes.length)
                ex.getResponseBody.write(bytes)
              else ex.sendResponseHeaders(404, -1)
            finally ex.close(),
        )
        server.setExecutor(null)
        server.start()
        server
      })(s => ZIO.succeed(s.stop(0)))
      .map(_.getAddress.getPort)
  end serve

  private def jarWith(dir: Path, name: String, version: String, path: String, body: String): Task[Path] =
    ZIO.attempt {
      val jar = dir.resolve(s"$name-$version.jar")
      val out = new JarOutputStream(Files.newOutputStream(jar))
      try
        val entry = new JarEntry(s"META-INF/resources/webjars/$name/$version/$path")
        out.putNextEntry(entry)
        out.write(body.getBytes(StandardCharsets.UTF_8))
        out.closeEntry()
      finally out.close()
      jar
    }

  private def hex(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString

  private def tempDir: UIO[Path] =
    ZIO.attempt(Files.createTempDirectory("sbt-splice-resolve-")).orDie

  private def write(path: Path, body: String): Task[Unit] =
    ZIO.attempt {
      Files.writeString(path, body)
      ()
    }
end ResolveSpec
