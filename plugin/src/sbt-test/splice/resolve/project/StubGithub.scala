import com.sun.net.httpserver.{HttpExchange, HttpServer}

import java.net.InetSocketAddress
import java.nio.file.{Files, Paths}
import java.security.MessageDigest

/** GitHub tag-archive shaped local HTTP: /{owner}/{repo}/archive/refs/tags/{tag}.tar.gz */
object StubGithub:
  val Archive: Array[Byte] =
    Files.readAllBytes(Paths.get("archive/foo.tar.gz"))

  val sha256: String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(Archive)
      .map("%02x".format(_))
      .mkString

  lazy val port: Int =
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/",
      (ex: HttpExchange) =>
        try
          if ex.getRequestURI.getPath.endsWith(".tar.gz") then
            ex.sendResponseHeaders(200, Archive.length)
            ex.getResponseBody.write(Archive)
          else ex.sendResponseHeaders(404, -1)
        finally ex.close()
      ,
    )
    server.setExecutor(null)
    server.start()
    server.getAddress.getPort
end StubGithub
