package scala.build.tests

import com.eed3si9n.expecty.Expecty.assert as expect
import coursier.{Repositories, Repository}
import coursier.cache.FileCache
import coursier.core.Version
import coursier.maven.MavenRepository
import dependency.ScalaParameters

import scala.build.Ops.*
import scala.build.errors.{
  InvalidBinaryScalaVersionError,
  NoValidScalaVersionFoundError,
  UnsupportedScalaVersionError
}
import scala.build.internal.Constants.*
import scala.build.internal.Regexes.scala2NightlyRegex
import scala.build.options.{
  BuildOptions,
  BuildRequirements,
  ClassPathOptions,
  InternalOptions,
  MaybeScalaVersion,
  ScalaOptions,
  ScalaVersionUtil,
  ScalacOpt,
  ShadowingSeq
}
import scala.build.{Build, BuildThreads, LocalRepo}
import scala.build.Directories
import scala.build.Positioned
import scala.build.tests.util.BloopServer
import scala.concurrent.duration.DurationInt
import scala.build.internal.Regexes.scala3LtsRegex
import scala.build.errors.ScalaVersionError

class BuildOptionsTests extends TestUtil.ScalaCliBuildSuite {
  override def munitFlakyOK: Boolean = TestUtil.isCI

  val extraRepoTmpDir = os.temp.dir(prefix = "scala-cli-tests-extra-repo-")
  val directories     = Directories.under(extraRepoTmpDir)
  val buildThreads    = BuildThreads.create()
  val baseOptions     = BuildOptions(
    internal = InternalOptions(
      localRepository = LocalRepo.localRepo(directories.localRepoDir, TestLogger()),
      keepDiagnostics = true
    )
  )
  def bloopConfigOpt            = Some(BloopServer.bloopConfig)
  override def afterAll(): Unit = {
    buildThreads.shutdown()
  }

  test("Empty BuildOptions is actually empty") {
    val empty = BuildOptions()
    val zero  = BuildOptions.monoid.zero
    expect(
      empty == zero,
      "Unexpected Option / Seq / Set / Boolean with a non-empty / non-false default value"
    )
  }

  test("-S 3.nightly option works") {
    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion("3.nightly"))
      )
    )
    val scalaParams = options.scalaParams.orThrow.getOrElse(???)
    assert(
      scalaParams.scalaVersion.startsWith("3") && scalaParams.scalaVersion.endsWith("-NIGHTLY"),
      "-S 3.nightly argument does not lead to scala3 nightly build option"
    )
  }
  test("-S 3.1.nightly option works") {
    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion("3.1.nightly"))
      )
    )
    val scalaParams = options.scalaParams.orThrow.getOrElse(???)
    expect(
      scalaParams.scalaVersion.startsWith("3.1.") && scalaParams.scalaVersion.endsWith("-NIGHTLY"),
      "-S 3.1.nightly argument does not lead to scala 3.1. nightly build option"
    )
  }

  test(s"Scala 3.${Int.MaxValue} shows Invalid Binary Scala Version Error") {

    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion(s"3.${Int.MaxValue}"))
      )
    )
    assert(
      options.projectParams.swap.exists {
        case _: InvalidBinaryScalaVersionError => true; case _ => false
      },
      s"specifying the 3.${Int.MaxValue} scala version does not lead to the Invalid Binary Scala Version Error"
    )
  }

  test(s"Scala 2.lts shows Scala Version Error") {

    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion(s"3.${Int.MaxValue}"))
      )
    )
    assert(
      options.projectParams.swap.exists {
        case _: ScalaVersionError => true; case _ => false
      },
      s"specifying 2.lts scala version does not lead to Scala Version Error"
    )
  }

  test("Scala 2.11.2 shows Unupported Scala Version Error") {

    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion("2.11.2"))
      )
    )

    assert(
      options.projectParams.swap.exists {
        case _: UnsupportedScalaVersionError => true; case _ => false
      },
      "specifying the 2.11.2 scala version does not lead to the Unsupported Scala Version Error"
    )
  }

  test("Scala 2.11 shows Unupported Scala Version Error") {

    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion("2.11"))
      )
    )
    assert(
      options.projectParams.swap.exists {
        case _: UnsupportedScalaVersionError => true; case _ => false
      },
      "specifying the 2.11 scala version does not lead to the Unsupported Scala Version Error"
    )
  }

  test(s"Scala 3.${Int.MaxValue}.3 shows Invalid Binary Scala Version Error") {

    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion(s"3.${Int.MaxValue}.3"))
      )
    )
    assert(
      options.projectParams.swap.exists {
        case _: InvalidBinaryScalaVersionError => true; case _ => false
      },
      "specifying the 3.2147483647.3 scala version does not lead to the Invalid Binary Scala Version Error"
    )
  }

  test("Scala 3.1.3-RC1-bin-20220213-fd97eee-NIGHTLY shows No Valid Scala Version Error") {

    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion("3.1.3-RC1-bin-20220213-fd97eee-NIGHTLY"))
      )
    )
    assert(
      options.projectParams.swap.exists {
        case _: NoValidScalaVersionFoundError => true; case _ => false
      },
      "specifying the wrong full scala 3 nightly version does not lead to the No Valid Scala Version Found Error"
    )
  }

  test("Scala 3.1.2-RC1 works") {

    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion("3.1.2-RC1"))
      )
    )
    val scalaParams = options.scalaParams.orThrow.getOrElse(???)
    assert(
      scalaParams.scalaVersion == "3.1.2-RC1",
      "-S 3.1.2-RC1 argument does not lead to 3.1.2-RC1 build option"
    )
  }

  test("Scala 2.12.9-bin-1111111 shows No Valid Scala Version Error") {

    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion("2.12.9-bin-1111111"))
      )
    )
    assert(
      options.projectParams.swap.exists {
        case _: NoValidScalaVersionFoundError => true; case _ => false
      },
      "specifying the wrong full scala 2 nightly version does not lead to the No Valid Scala Version Found Error"
    )
  }

  test(s"Scala 2.${Int.MaxValue} shows Invalid Binary Scala Version Error") {

    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion(s"2.${Int.MaxValue}"))
      )
    )
    assert(
      options.projectParams.swap.exists {
        case _: InvalidBinaryScalaVersionError => true; case _ => false
      },
      s"specifying 2.${Int.MaxValue} as Scala version does not lead to Invalid Binary Scala Version Error"
    )
  }

  test("-S 2.nightly option works".flaky) {
    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion("2.nightly"))
      )
    )
    val scalaParams = options.scalaParams.orThrow.getOrElse(???)
    assert(
      scala2NightlyRegex.unapplySeq(scalaParams.scalaVersion).isDefined,
      "-S 2.nightly argument does not lead to scala2 nightly build option"
    )
  }

  test("-S 2.13.nightly option works".flaky) {
    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion("2.13.nightly"))
      )
    )
    val scalaParams = options.scalaParams.orThrow.getOrElse(???)
    assert(
      scala2NightlyRegex.unapplySeq(scalaParams.scalaVersion).isDefined,
      "-S 2.13.nightly argument does not lead to scala2 nightly build option"
    )
  }

  test("-S 3.lts option works") {
    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion("3.lts"))
      )
    )
    val scalaParams = options.scalaParams.orThrow.getOrElse(???)
    assert(
      scala3LtsRegex.unapplySeq(scalaParams.scalaVersion).isDefined,
      "-S 3.lts argument does not lead to scala3 LTS"
    )
  }

  test("-S 2.12.nightly option works".flaky) {
    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion("2.12.nightly"))
      )
    )
    val scalaParams = options.scalaParams.orThrow.getOrElse(???)
    assert(
      scala2NightlyRegex.unapplySeq(scalaParams.scalaVersion).isDefined,
      "-S 2.12.nightly argument does not lead to scala2 nightly build option"
    )
  }

  test("-S 2.13.9-bin-4505094 option works without repo specification".flaky) {
    val options = BuildOptions(
      scalaOptions = ScalaOptions(
        scalaVersion = Some(MaybeScalaVersion("2.13.9-bin-4505094"))
      )
    )
    val scalaParams = options.scalaParams.orThrow.getOrElse(???)
    assert(
      scalaParams.scalaVersion == "2.13.9-bin-4505094",
      "-S 2.13.9-bin-4505094 argument does not lead to 2.13.9-bin-4505094 scala version in build option"
    )
  }

  test("Empty BuildRequirements is actually empty") {
    val empty = BuildRequirements()
    val zero  = BuildRequirements.monoid.zero
    expect(
      empty == zero,
      "Unexpected Option / Seq / Set / Boolean with a non-empty / non-false default value"
    )
  }

  val expectedScalaVersions = Seq(
    None           -> defaultScalaVersion,
    Some("2.13.2") -> "2.13.2",
    Some("3.0.1")  -> "3.0.1",
    Some("3.0")    -> "3.0.2"
  )

  for ((prefix, expectedScalaVersion) <- expectedScalaVersions)
    test(
      s"use scala $expectedScalaVersion for prefix scala version: ${prefix.getOrElse("empty")}"
    ) {
      val options = BuildOptions(
        scalaOptions = ScalaOptions(
          scalaVersion = prefix.map(MaybeScalaVersion(_))
        ),
        internal = InternalOptions(
          cache = Some(FileCache().withTtl(0.seconds))
        )
      )
      val scalaParams = options.scalaParams.orThrow.getOrElse(???)

      val expectedScalaParams = ScalaParameters(expectedScalaVersion)

      expect(scalaParams == expectedScalaParams)
    }

  {
    val cache        = FileCache().withTtl(0.seconds)
    val repositories = BuildOptions(
      internal = InternalOptions(cache = Some(cache)),
      classPathOptions =
        ClassPathOptions(extraRepositories = Seq(coursier.Repositories.scalaIntegration.root))
    ).finalRepositories.orThrow
    val allScalaVersions = ScalaVersionUtil.allMatchingVersions(None, cache, repositories)
    for {
      (prefix, defaultMatchingVersion, predefinedDefaultScalaVersion) <- {
        extension (nightlies: Seq[String])
          private def latestNightly: Option[String] =
            if nightlies.nonEmpty then Some(nightlies.maxBy(Version(_))) else None
        val scala2Nightlies     = allScalaVersions.filter(ScalaVersionUtil.isScala2Nightly)
        val scala212Nightlies   = scala2Nightlies.filter(_.startsWith("2.12"))
        val scala213Nightlies   = scala2Nightlies.filter(_.startsWith("2.13"))
        val scala3Nightlies     = allScalaVersions.filter(ScalaVersionUtil.isScala3Nightly)
        val scala3NextNightlies = scala3Nightlies.filter(_.startsWith(scala3NextPrefix))
        Seq(
          ("2.12", defaultScala212Version, None),
          ("2.12", defaultScala212Version, scala212Nightlies.latestNightly),
          ("2.13", defaultScala213Version, None),
          ("2.13", defaultScala213Version, scala213Nightlies.latestNightly),
          ("3", defaultScalaVersion, None),
          (scala3NextPrefix, defaultScalaVersion, None),
          (scala3NextPrefix, defaultScalaVersion, scala3NextNightlies.latestNightly)
        ).distinct
      }
      options = BuildOptions(
        scalaOptions = ScalaOptions(
          scalaVersion = Some(prefix).map(MaybeScalaVersion(_)),
          defaultScalaVersion = predefinedDefaultScalaVersion
        ),
        internal = InternalOptions(
          cache = Some(cache)
        ),
        classPathOptions = ClassPathOptions(
          extraRepositories = Seq(coursier.Repositories.scalaIntegration.root)
        )
      )
      latestMatchingVersion = allScalaVersions
        .filter(ScalaVersionUtil.isStable)
        .filter(_.startsWith(prefix))
        .maxBy(Version(_))
      expectedVersion            = predefinedDefaultScalaVersion.getOrElse(defaultMatchingVersion)
      expectedVersionDescription =
        if expectedVersion == defaultMatchingVersion then "default" else "overridden default"
      launcherDefaultVersionDescription = if expectedVersion == defaultMatchingVersion then ""
      else s"or the launcher default ($defaultMatchingVersion)"
      testDescription =
        s"-S $prefix should choose the $expectedVersionDescription version ($expectedVersion), not necessarily the latest stable ($latestMatchingVersion) $launcherDefaultVersionDescription"
    } test(testDescription) {
      val scalaParams = options.scalaParams.orThrow.getOrElse(???)

      val expectedScalaParams = ScalaParameters(expectedVersion)

      expect(scalaParams == expectedScalaParams, s"expected $expectedScalaParams, got $scalaParams")
    }
  }

  test("User scalac options shadow internal ones") {
    val defaultOptions = BuildOptions(
      internal = InternalOptions(
        localRepository = LocalRepo.localRepo(directories.localRepoDir, TestLogger())
      )
    )

    val newSourceRoot = os.pwd / "out" / "foo"

    val extraScalacOpt = Seq("-sourceroot", newSourceRoot.toString)
    val options        = defaultOptions.copy(
      scalaOptions = defaultOptions.scalaOptions.copy(
        scalaVersion = Some(MaybeScalaVersion("3.1.1")),
        scalacOptions = ShadowingSeq.from(
          extraScalacOpt
            .map(ScalacOpt(_))
            .map(Positioned.none)
        )
      )
    )

    val dummyInputs = TestInputs(
      os.rel / "Foo.scala" ->
        """object Foo
          |""".stripMargin
    )

    dummyInputs.withLoadedBuild(options, buildThreads, None) {
      (_, _, build) =>

        val build0 = build match {
          case s: Build.Successful => s
          case _                   => sys.error(s"Unexpected failed or cancelled build $build")
        }

        val rawOptions = build0.project.scalaCompiler.toSeq.flatMap(_.scalacOptions)
        val seq        = ShadowingSeq.from(rawOptions.map(ScalacOpt(_)))

        expect(seq.toSeq.length == rawOptions.length) // no option needs to be shadowed

        pprint.err.log(rawOptions)
        expect(rawOptions.containsSlice(extraScalacOpt))
    }
  }

  test("parse snapshots repository") {
    val inputs = TestInputs(
      os.rel / "Foo.scala" ->
        """//> using repository snapshots
          |//> using repository central
          |object Foo extends App {
          |  println("Hello")
          |}
          |""".stripMargin
    )

    inputs.withBuild(BuildOptions(), buildThreads, bloopConfigOpt, buildTests = false) {
      (_, _, maybeBuild) =>
        expect(maybeBuild.exists(_.success))
        val build = maybeBuild
          .toOption
          .flatMap(_.successfulOpt)
          .getOrElse(sys.error("cannot happen"))
        val repositories = build.options.finalRepositories.orThrow

        expect(repositories.length == 4)
        expect(repositories.contains(Repositories.sonatype("snapshots")))
        expect(repositories.contains(Repositories.sonatypeS01("snapshots")))
        expect(repositories.contains(Repositories.central))
        expect(repositories.contains(
          MavenRepository("https://central.sonatype.com/repository/maven-snapshots")
        ))
    }
  }

  test("resolve semanticDB for older scala version") {
    val buildOptions = BuildOptions()
    val scalaVersion = "2.13.3"

    val semanticDbVersion = buildOptions.findSemanticDbVersion(scalaVersion, TestLogger())
    expect(semanticDbVersion == "4.8.4")
  }

  test("skip setting release option when -release or -java-output-version is set by user") {
    val javaOutputVersionOpt = "-java-output-version:16"
    val inputs               = TestInputs(
      os.rel / "Hello.scala" ->
        s"""//> using option $javaOutputVersionOpt
           |""".stripMargin
    )

    inputs.withBuild(BuildOptions(), buildThreads, bloopConfigOpt, buildTests = false) {
      (_, _, maybeBuild) =>
        expect(maybeBuild.exists(_.success))
        val build = maybeBuild
          .toOption
          .flatMap(_.successfulOpt)
          .getOrElse(sys.error("cannot happen"))

        val scalacOpts = build.project.scalaCompiler.toSeq.flatMap(_.scalacOptions)

        expect(scalacOpts.contains(javaOutputVersionOpt))
        expect(!scalacOpts.exists(_.startsWith("-release")))
    }
  }

}
