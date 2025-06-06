package scala.build

import coursier.core.Module
import coursier.parse.ModuleParser

import scala.build.internal.Constants
import scala.quoted.*

def noArgs(args: Expr[Seq[Any]])(using Quotes): Unit = {} // TODO

def extractString(cs: Expr[StringContext])(using Quotes): String =
  cs.value match {
    // without Seq it brokes compiler! - check this!
    case Some(StringContext(Seq(part: String))) =>
      part
    case _ =>
      quotes.reflect.report.error("StringContext args must be statically known")
      ???
  }

object CoursierUtils {
  def parseModule(cs: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Module] =
    noArgs(args)
    val modString = extractString(cs)
    ModuleParser.module(modString, Constants.defaultScalaVersion) match {
      case Left(error) =>
        quotes.reflect.report.error(error)
        ???
      case Right(_) =>
        '{
          ModuleParser.module(
            ${ Expr(modString) },
            Constants.defaultScalaVersion
          ).getOrElse(???)
        }
    }

  extension (inline ctx: StringContext)
    inline def cmod(inline args: Any*): Module =
      ${ parseModule('ctx, 'args) }
}
