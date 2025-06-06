package scala.cli.commands.bloop

import bloop.rifle.internal.BuildInfo
import bloop.rifle.{BloopRifle, BloopRifleConfig, BloopThreads}
import caseapp.*

import scala.build.options.{BuildOptions, InternalOptions}
import scala.build.{Directories, Logger, Os}
import scala.cli.commands.ScalaCommand
import scala.cli.commands.util.JvmUtils
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object BloopStart extends ScalaCommand[BloopStartOptions] {
  override def hidden                  = true
  override def scalaSpecificationLevel = SpecificationLevel.RESTRICTED
  override def names: List[List[String]] = List(
    List("bloop", "start")
  )

  private def mkBloopRifleConfig(opts: BloopStartOptions): BloopRifleConfig = {
    import opts.*
    val buildOptions = BuildOptions(
      javaOptions = JvmUtils.javaOptions(jvm).orExit(global.logging.logger),
      internal = InternalOptions(
        cache = Some(coursier.coursierCache(global.logging.logger.coursierLogger("")))
      )
    )

    compilationServer.bloopRifleConfig(
      global.logging.logger,
      coursier.coursierCache(global.logging.logger.coursierLogger("Downloading Bloop")),
      global.logging.verbosity,
      buildOptions.javaHome().value.javaCommand,
      Directories.directories
    )
  }

  override def runCommand(options: BloopStartOptions, args: RemainingArgs, logger: Logger): Unit = {
    val threads          = BloopThreads.create()
    val bloopRifleConfig = mkBloopRifleConfig(options)

    val isRunning = BloopRifle.check(bloopRifleConfig, logger.bloopRifleLogger)

    if (isRunning && options.force) {
      logger.message("Found Bloop server running, stopping it.")
      val ret = BloopRifle.exit(bloopRifleConfig, Os.pwd.toNIO, logger.bloopRifleLogger)
      logger.debug(s"Bloop exit returned code $ret")
      if (ret == 0)
        logger.message("Stopped Bloop server.")
      else {
        if (options.global.logging.verbosity >= 0)
          System.err.println(s"Error running bloop exit command (return code $ret)")
        sys.exit(1)
      }
    }

    if (isRunning && !options.force)
      logger.message("Bloop server already running.")
    else {

      val f = BloopRifle.startServer(
        bloopRifleConfig,
        threads.startServerChecks,
        logger.bloopRifleLogger,
        BuildInfo.version,
        bloopRifleConfig.javaPath
      )
      Await.result(f, Duration.Inf)
      logger.message("Bloop server started.")
    }
  }
}
