package scala.cli.commands.pgp

import caseapp.core.RemainingArgs

import scala.cli.signing.commands.{PgpCreate as OriginalPgpCreate, PgpCreateOptions}

object PgpCreate extends PgpCommand[PgpCreateOptions] {
  override def names = PgpCommandNames.pgpCreate

  override def run(options: PgpCreateOptions, args: RemainingArgs): Unit =
    OriginalPgpCreate.run(options, args)
}
