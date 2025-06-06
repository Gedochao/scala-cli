package sclicheck

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

class DocTests extends munit.FunSuite {
  override def munitTimeout = new FiniteDuration(480, TimeUnit.SECONDS)
  case class DocTestEntry(name: String, path: os.Path, depth: Int = Int.MaxValue)

  val docsRootPath: os.Path      = os.Path(sys.env("MILL_WORKSPACE_ROOT")) / "website" / "docs"
  val entries: Seq[DocTestEntry] = Seq(
    DocTestEntry("root", docsRootPath, depth = 1),
    DocTestEntry("cookbook", docsRootPath / "cookbooks"),
    DocTestEntry("command", docsRootPath / "commands"),
    DocTestEntry("guide", docsRootPath / "guides"),
    DocTestEntry("reference", docsRootPath / "reference")
  )

  val options: Options = Options(scalaCliCommand = Seq(TestUtil.scalaCliPath.toString))

  private def lineContainsAnyChecks(l: String): Boolean =
    l.startsWith("```md") || l.startsWith("```bash") ||
    l.startsWith("```scala compile") || l.startsWith("```scala fail") ||
    l.startsWith("````markdown compile") || l.startsWith("````markdown fail") ||
    l.startsWith("```java compile") || l.startsWith("````java fail")
  private def fileContainsAnyChecks(f: os.Path): Boolean =
    os.read.lines(f).exists(lineContainsAnyChecks)

  for {
    DocTestEntry(tpe, dir, depth) <- entries
    inputs = os.walk(dir, maxDepth = depth)
      .filter(_.last.endsWith(".md"))
      .filter(os.isFile(_))
      .filter(fileContainsAnyChecks)
      .map(_.relativeTo(dir))
      .sortBy(_.toString)
    md <- inputs
  }
    test(s"$tpe ${md.toString.stripSuffix(".md")}") {
      TestUtil.retryOnCi()(checkFile(dir / md, options))
    }

}
