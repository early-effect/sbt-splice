import sbt.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.jar.{JarEntry, JarOutputStream}

/** Tiny org.webjars.npm:foo:1.0.0 jar for scripted, no Central. */
object WebjarRepo:
  def write(repo: File): Unit =
    val dest = repo / "org" / "webjars" / "npm" / "foo" / "1.0.0"
    dest.mkdirs()
    val jar = dest / "foo-1.0.0.jar"
    val out = new JarOutputStream(Files.newOutputStream(jar.toPath))
    try
      val entry = new JarEntry("META-INF/resources/webjars/foo/1.0.0/foo.js")
      out.putNextEntry(entry)
      out.write("""export function greet() { return "ok"; }""".getBytes(StandardCharsets.UTF_8))
      out.closeEntry()
    finally out.close()
    IO.write(
      dest / "foo-1.0.0.pom",
      """<project>
        |  <modelVersion>4.0.0</modelVersion>
        |  <groupId>org.webjars.npm</groupId>
        |  <artifactId>foo</artifactId>
        |  <version>1.0.0</version>
        |</project>
        |""".stripMargin,
    )
end WebjarRepo
