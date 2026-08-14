import com.sun.net.httpserver.{HttpExchange, HttpServer}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

object StubCdn:
  val Body: String =
    """export function greet() { return "ok"; }"""

  lazy val port: Int =
    val bytes  = Body.getBytes(StandardCharsets.UTF_8)
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/",
      (ex: HttpExchange) =>
        try
          if ex.getRequestURI.getPath.endsWith("/foo.js") then
            ex.sendResponseHeaders(200, bytes.length)
            ex.getResponseBody.write(bytes)
          else ex.sendResponseHeaders(404, -1)
        finally ex.close()
      ,
    )
    server.setExecutor(null)
    server.start()
    server.getAddress.getPort
end StubCdn
