package scala.build.errors

final class MultipleScalaVersionsError(scalaVersions: Seq[String])
    extends BuildException(
      message =
        s"Multiple Scala versions are present in the build (${scalaVersions.mkString(" ")}), even though only one is allowed in this context.",
      positions = Nil
    )
