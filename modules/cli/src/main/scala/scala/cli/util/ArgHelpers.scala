package scala.cli.util

import caseapp.core.Arg
import caseapp.core.help.HelpFormat
import caseapp.core.util.CaseUtil

import scala.build.input.ScalaCliInvokeData
import scala.build.internal.util.WarningMessages
import scala.cli.ScalaCli
import scala.cli.commands.shared.{HelpCommandGroup, HelpGroup}
import scala.cli.commands.{SpecificationLevel, tags}

object ArgHelpers {
  extension (arg: Arg) {
    private def hasTag(tag: String): Boolean             = arg.tags.exists(_.name == tag)
    private def hasTagPrefix(tagPrefix: String): Boolean =
      arg.tags.exists(_.name.startsWith(tagPrefix))
    def isExperimental: Boolean = arg.hasTag(tags.experimental)
    def isRestricted: Boolean   = arg.hasTag(tags.restricted)
    def isDeprecated: Boolean   = arg.hasTagPrefix(tags.deprecatedPrefix)

    def deprecatedNames: List[String] = arg.tags
      .filter(_.name.startsWith(tags.deprecatedPrefix))
      .map(_.name.stripPrefix(s"${tags.deprecatedPrefix}${tags.valueSeparator}"))
      .toList

    def deprecatedOptionAliases: List[String] = arg.deprecatedNames.map {
      case name if name.startsWith("-") => name
      case name if name.length == 1     => "-" + name
      case name => "--" + CaseUtil.pascalCaseSplit(name.toCharArray.toList).map(
          _.toLowerCase
        ).mkString("-")
    }

    def isExperimentalOrRestricted: Boolean = arg.isRestricted || arg.isExperimental

    def isSupported: Boolean = ScalaCli.allowRestrictedFeatures || !arg.isExperimentalOrRestricted
    def isImportant: Boolean = arg.hasTag(tags.inShortHelp)

    def isMust: Boolean = arg.hasTag(tags.must)

    def level: SpecificationLevel = arg.tags
      .flatMap(t => tags.levelFor(t.name))
      .headOption
      .getOrElse(SpecificationLevel.IMPLEMENTATION)
    def powerOptionUsedInSip(optionName: String)(using ScalaCliInvokeData): String = {
      val specificationLevel =
        if arg.isExperimental then SpecificationLevel.EXPERIMENTAL
        else if arg.isRestricted then SpecificationLevel.RESTRICTED
        else
          arg.tags
            .flatMap(t => tags.levelFor(t.name))
            .headOption
            .getOrElse(SpecificationLevel.EXPERIMENTAL)
      WarningMessages.powerOptionUsedInSip(optionName, specificationLevel)
    }
  }

  extension (helpFormat: HelpFormat) {
    def withPrimaryGroup(primaryGroup: HelpGroup): HelpFormat =
      helpFormat.withPrimaryGroups(Seq(primaryGroup))
    def withPrimaryGroups(primaryGroups: Seq[HelpGroup]): HelpFormat = {
      val primaryStringGroups     = primaryGroups.map(_.toString)
      val oldSortedGroups         = helpFormat.sortedGroups.getOrElse(Seq.empty)
      val filteredOldSortedGroups = oldSortedGroups.filterNot(primaryStringGroups.contains)
      helpFormat.copy(sortedGroups = Some(primaryStringGroups ++ filteredOldSortedGroups))
    }
    def withHiddenGroups(hiddenGroups: Seq[HelpGroup]): HelpFormat =
      helpFormat.copy(hiddenGroups = Some(hiddenGroups.map(_.toString)))

    def withHiddenGroup(hiddenGroup: HelpGroup): HelpFormat =
      helpFormat.withHiddenGroups(Seq(hiddenGroup))
    def withHiddenGroupsWhenShowHidden(hiddenGroups: Seq[HelpGroup]): HelpFormat =
      helpFormat.copy(hiddenGroupsWhenShowHidden = Some(hiddenGroups.map(_.toString)))
    def withHiddenGroupWhenShowHidden(hiddenGroup: HelpGroup): HelpFormat =
      helpFormat.withHiddenGroupsWhenShowHidden(Seq(hiddenGroup))
    def withSortedGroups(sortedGroups: Seq[HelpGroup]): HelpFormat =
      helpFormat.copy(sortedGroups = Some(sortedGroups.map(_.toString)))
    def withSortedCommandGroups(sortedGroups: Seq[HelpCommandGroup]): HelpFormat =
      helpFormat.copy(sortedCommandGroups = Some(sortedGroups.map(_.toString)))
    def withNamesLimit(namesLimit: Int): HelpFormat =
      helpFormat.copy(namesLimit = Some(namesLimit))
  }
}
