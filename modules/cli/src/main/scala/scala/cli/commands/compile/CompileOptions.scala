package scala.cli.commands.compile

import caseapp.*
import caseapp.core.help.Help

import scala.cli.commands.shared.{
  CrossOptions,
  HasSharedOptions,
  HelpGroup,
  HelpMessages,
  SharedOptions,
  SharedWatchOptions
}
import scala.cli.commands.tags

@HelpMessage(CompileOptions.helpMessage, "", CompileOptions.detailedHelpMessage)
// format: off
final case class CompileOptions(
  @Recurse
    shared: SharedOptions = SharedOptions(),
  @Recurse
    watch: SharedWatchOptions = SharedWatchOptions(),
  @Recurse
    cross: CrossOptions = CrossOptions(),

  @Group(HelpGroup.Compilation.toString)
  @Name("p")
  @Name("printClasspath")
  @HelpMessage("Print the resulting class path")
  @Tag(tags.should)
  @Tag(tags.inShortHelp)
    printClassPath: Boolean = false,

  @Group(HelpGroup.Compilation.toString)
  @HelpMessage("Compile test scope")
  @Tag(tags.should)
  @Tag(tags.inShortHelp)
    test: Boolean = false,

  @Group(HelpGroup.Compilation.toString)
  @HelpMessage("some option")
  @Tag(tags.should)
    someOption: Boolean = false,

  @Group(HelpGroup.Compilation.toString)
  @HelpMessage("duplicate")
  @Tag(tags.should)
  @Name("someOption")
    duplicateOption: Boolean = false
) extends HasSharedOptions
  // format: on

object CompileOptions {
  implicit lazy val parser: Parser[CompileOptions] = Parser.derive
  implicit lazy val help: Help[CompileOptions]     = Help.derive
  val cmdName                                      = "compile"
  private val helpHeader                           = "Compile Scala code."
  val helpMessage: String = HelpMessages.shortHelpMessage(cmdName, helpHeader)
  val detailedHelpMessage: String =
    s"""$helpHeader
       |
       |${HelpMessages.commandConfigurations(cmdName)}
       |
       |${HelpMessages.acceptedInputs}
       |
       |${HelpMessages.commandDocWebsiteReference(cmdName)}""".stripMargin
}
