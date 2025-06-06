package scala.cli.commands.dependencyupdate

import caseapp.*
import caseapp.core.help.HelpFormat

import scala.build.actionable.ActionableDependencyHandler
import scala.build.actionable.ActionableDiagnostic.ActionableDependencyUpdateDiagnostic
import scala.build.internals.ConsoleUtils.ScalaCliConsole.warnPrefix
import scala.build.options.Scope
import scala.build.{CrossSources, Logger, Position, Sources}
import scala.cli.commands.shared.{HelpCommandGroup, HelpGroup, SharedOptions}
import scala.cli.commands.{ScalaCommand, SpecificationLevel}
import scala.cli.util.ArgHelpers.*

object DependencyUpdate extends ScalaCommand[DependencyUpdateOptions] {
  override def group: String                               = HelpCommandGroup.Main.toString
  override def scalaSpecificationLevel: SpecificationLevel = SpecificationLevel.RESTRICTED
  override def helpFormat: HelpFormat                      =
    super.helpFormat.withPrimaryGroup(HelpGroup.Dependency)
  override def sharedOptions(options: DependencyUpdateOptions): Option[SharedOptions] =
    Some(options.shared)
  override def runCommand(
    options: DependencyUpdateOptions,
    args: RemainingArgs,
    logger: Logger
  ): Unit = {
    if options.shared.scope.test.nonEmpty then
      logger.message(
        s"""$warnPrefix Including the test scope does not change the behaviour of this command. 
           |$warnPrefix Test dependencies are updated regardless.""".stripMargin
      )
    val verbosity    = options.shared.logging.verbosity
    val buildOptions = buildOptionsOrExit(options)

    val inputs = options.shared.inputs(args.all).orExit(logger)

    val (crossSources, _) =
      CrossSources.forInputs(
        inputs,
        Sources.defaultPreprocessors(
          buildOptions.archiveCache,
          buildOptions.internal.javaClassNameVersionOpt,
          () => buildOptions.javaHome().value.javaCommand
        ),
        logger,
        buildOptions.suppressWarningOptions,
        buildOptions.internal.exclude,
        download = buildOptions.downloader
      ).orExit(logger)

    val sharedOptions = crossSources.sharedOptions(buildOptions)
    val scopedSources = crossSources.scopedSources(buildOptions).orExit(logger)

    def generateActionableUpdateDiagnostic(scope: Scope)
      : Seq[ActionableDependencyUpdateDiagnostic] = {
      val sources =
        scopedSources.sources(scope, sharedOptions, inputs.workspace, logger).orExit(logger)

      if (verbosity >= 3)
        pprint.err.log(sources)

      val options = buildOptions.orElse(sources.buildOptions)
      ActionableDependencyHandler.createActionableDiagnostics(options, Some(logger)).orExit(logger)
    }

    val actionableMainUpdateDiagnostics = generateActionableUpdateDiagnostic(Scope.Main)
    val actionableTestUpdateDiagnostics = generateActionableUpdateDiagnostic(Scope.Test)
    val actionableUpdateDiagnostics     =
      (actionableMainUpdateDiagnostics ++ actionableTestUpdateDiagnostics).distinct

    if (options.all)
      updateDependencies(actionableUpdateDiagnostics, logger)
    else {
      println("Updates")
      actionableUpdateDiagnostics.foreach(update =>
        println(
          s"   * ${update.dependencyModuleName} ${update.currentVersion} -> ${update.newVersion}"
        )
      )
      println(s"""|To update all dependencies run:
                  |    $baseRunnerName dependency-update --all""".stripMargin)
    }
  }

  private def updateDependencies(
    actionableUpdateDiagnostics: Seq[ActionableDependencyUpdateDiagnostic],
    logger: Logger
  ): Unit = {
    val groupedByFileDiagnostics =
      actionableUpdateDiagnostics.flatMap {
        diagnostic =>
          diagnostic.positions.collect {
            case file: Position.File =>
              file.path -> (file, diagnostic)
          }
      }.groupMap(_._1)(_._2)

    groupedByFileDiagnostics.foreach {
      case (Right(file), diagnostics) =>
        val sortedByLine       = diagnostics.sortBy(_._1.startPos._1).reverse
        val appliedDiagnostics = updateDependencies(file, sortedByLine)
        os.write.over(file, appliedDiagnostics)
        diagnostics.foreach(diagnostic =>
          logger.message(
            s"Updated dependency ${diagnostic._2.dependencyModuleName}: ${diagnostic._2
                .currentVersion} -> ${diagnostic._2.newVersion}"
          )
        )
      case (Left(file), diagnostics) =>
        diagnostics.foreach {
          diagnostic =>
            logger.message(
              s"Warning: $fullRunnerName can't update ${diagnostic._2.suggestion} in $file"
            )
        }
    }
  }

  private def updateDependencies(
    file: os.Path,
    diagnostics: Seq[(Position.File, ActionableDependencyUpdateDiagnostic)]
  ): String = {
    val fileContent   = os.read(file)
    val startIndicies = Position.Raw.lineStartIndices(fileContent)

    diagnostics.foldLeft(fileContent) {
      case (fileContent, (file, diagnostic)) =>
        val (line, column)       = (file.startPos._1, file.startPos._2)
        val (lineEnd, columnEnd) = (file.endPos._1, file.endPos._2)
        val startIndex           = startIndicies(line) + column
        val endIndex             = startIndicies(lineEnd) + columnEnd

        val newDependency = diagnostic.suggestion
        s"${fileContent.slice(0, startIndex)}$newDependency${fileContent.drop(endIndex)}"
    }
  }

}
