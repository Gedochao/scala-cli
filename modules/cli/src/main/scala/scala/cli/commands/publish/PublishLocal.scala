package scala.cli.commands.publish

import caseapp.core.RemainingArgs
import caseapp.core.help.HelpFormat

import scala.build.{BuildThreads, Logger}
import scala.cli.CurrentParams
import scala.cli.commands.ScalaCommand
import scala.cli.commands.shared.{HelpCommandGroup, SharedOptions}
import scala.cli.config.ConfigDb
import scala.cli.util.ArgHelpers.*

object PublishLocal extends ScalaCommand[PublishLocalOptions] {
  override def group: String           = HelpCommandGroup.Main.toString
  override def scalaSpecificationLevel = SpecificationLevel.EXPERIMENTAL
  override def helpFormat: HelpFormat  =
    super.helpFormat
      .withHiddenGroups(Publish.hiddenHelpGroups)
      .withPrimaryGroups(Publish.primaryHelpGroups)
  override def sharedOptions(options: PublishLocalOptions): Option[SharedOptions] =
    Some(options.shared)

  override def names: List[List[String]] = List(
    List("publish", "local")
  )

  override def runCommand(
    options: PublishLocalOptions,
    args: RemainingArgs,
    logger: Logger
  ): Unit = {
    Publish.maybePrintLicensesAndExit(options.publishParams)
    Publish.maybePrintChecksumsAndExit(options.sharedPublish)

    val baseOptions = buildOptionsOrExit(options)
    val inputs      = options.shared.inputs(args.all).orExit(logger)
    CurrentParams.workspaceOpt = Some(inputs.workspace)

    val initialBuildOptions = Publish.mkBuildOptions(
      baseOptions,
      options.shared.sharedVersionOptions,
      options.publishParams,
      options.sharedPublish,
      PublishRepositoryOptions(),
      options.scalaSigning,
      PublishConnectionOptions(),
      options.mainClass,
      None
    ).orExit(logger)
    val threads = BuildThreads.create()

    val compilerMaker       = options.shared.compilerMaker(threads)
    val docCompilerMakerOpt = options.sharedPublish.docCompilerMakerOpt

    val cross = options.compileCross.cross.getOrElse(false)

    lazy val workingDir = options.sharedPublish.workingDir
      .filter(_.trim.nonEmpty)
      .map(os.Path(_, os.pwd))
      .getOrElse {
        os.temp.dir(
          prefix = "scala-cli-publish-",
          deleteOnExit = true
        )
      }

    val ivy2HomeOpt = options.sharedPublish.ivy2Home
      .filter(_.trim.nonEmpty)
      .map(os.Path(_, os.pwd))

    Publish.doRun(
      inputs = inputs,
      logger = logger,
      initialBuildOptions = initialBuildOptions,
      compilerMaker = compilerMaker,
      docCompilerMaker = docCompilerMakerOpt,
      cross = cross,
      workingDir = workingDir,
      ivy2HomeOpt = ivy2HomeOpt,
      publishLocal = true,
      forceSigningExternally = options.scalaSigning.forceSigningExternally.getOrElse(false),
      parallelUpload = Some(true),
      watch = options.watch.watch,
      isCi = options.publishParams.isCi,
      configDb = () => ConfigDb.empty, // shouldn't be used, no need of repo credentials here
      mainClassOptions = options.mainClass,
      dummy = options.sharedPublish.dummy,
      buildTests = options.shared.scope.test.getOrElse(false)
    )
  }
}
