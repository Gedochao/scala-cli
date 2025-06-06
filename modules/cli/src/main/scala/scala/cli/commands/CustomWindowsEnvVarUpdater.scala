package scala.cli.commands

import coursier.env.*

// Only using this instead of coursier.env.WindowsEnvVarUpdater for the "\u0000" striping thing,
// that earlier version of the Scala CLI may have left behind.
// We should be able to switch back to coursier.env.WindowsEnvVarUpdater
// after a bit of time (once super early users used this code more).

case class CustomWindowsEnvVarUpdater(
  powershellRunner: PowershellRunner = PowershellRunner(),
  useJni: Option[Boolean] = None
) extends EnvVarUpdater {

  def withUseJni(opt: Option[Boolean]) = copy(useJni = opt)

  private lazy val useJni0 = useJni.getOrElse {
    // FIXME Should be coursier.paths.Util.useJni(), but it's not available from here.
    !System.getProperty("coursier.jni", "").equalsIgnoreCase("false")
  }

  // https://stackoverflow.com/questions/9546324/adding-directory-to-path-environment-variable-in-windows/29109007#29109007
  // https://docs.microsoft.com/fr-fr/dotnet/api/system.environment.getenvironmentvariable?view=netframework-4.8#System_Environment_GetEnvironmentVariable_System_String_System_EnvironmentVariableTarget_
  // https://docs.microsoft.com/fr-fr/dotnet/api/system.environment.setenvironmentvariable?view=netframework-4.8#System_Environment_SetEnvironmentVariable_System_String_System_String_System_EnvironmentVariableTarget_

  private def getEnvironmentVariable(name: String): Option[String] =
    if (useJni0)
      Option(coursier.jniutils.WindowsEnvironmentVariables.get(name))
    else {
      val output = powershellRunner
        .runScript(CustomWindowsEnvVarUpdater.getEnvVarScript(name))
        .stripSuffix(System.lineSeparator())
      if (output == "null") // if ever the actual value is "null", we'll miss it
        None
      else
        Some(output)
    }

  private def setEnvironmentVariable(name: String, value: String): Unit =
    if (useJni0) {
      val value0 =
        if (value.contains("\u0000"))
          value.split(';').filter(!_.contains("\u0000")).mkString(";")
        else
          value
      coursier.jniutils.WindowsEnvironmentVariables.set(name, value0)
    }
    else
      powershellRunner.runScript(CustomWindowsEnvVarUpdater.setEnvVarScript(name, value))

  private def clearEnvironmentVariable(name: String): Unit =
    if (useJni0)
      coursier.jniutils.WindowsEnvironmentVariables.delete(name)
    else
      powershellRunner.runScript(CustomWindowsEnvVarUpdater.clearEnvVarScript(name))

  def applyUpdate(update: EnvironmentUpdate): Boolean = {

    // Beware, these are not an atomic operation overall
    // (we might discard values added by others between our get and our set)

    var setSomething = false

    for ((k, v) <- update.set) {
      val formerValueOpt = getEnvironmentVariable(k)
      val needsUpdate    = formerValueOpt.forall(_ != v)
      if (needsUpdate) {
        setEnvironmentVariable(k, v)
        setSomething = true
      }
    }

    for ((k, v) <- update.pathLikeAppends) {
      val formerValueOpt = getEnvironmentVariable(k)
      val alreadyInList  = formerValueOpt
        .exists(_.split(CustomWindowsEnvVarUpdater.windowsPathSeparator).contains(v))
      if (!alreadyInList) {
        val newValue = formerValueOpt
          .fold(v)(_ + CustomWindowsEnvVarUpdater.windowsPathSeparator + v)
        setEnvironmentVariable(k, newValue)
        setSomething = true
      }
    }

    setSomething
  }

  def tryRevertUpdate(update: EnvironmentUpdate): Boolean = {

    // Beware, these are not an atomic operation overall
    // (we might discard values added by others between our get and our set)

    var setSomething = false

    for ((k, v) <- update.set) {
      val formerValueOpt = getEnvironmentVariable(k)
      val wasUpdated     = formerValueOpt.exists(_ == v)
      if (wasUpdated) {
        clearEnvironmentVariable(k)
        setSomething = true
      }
    }

    for ((k, v) <- update.pathLikeAppends; formerValue <- getEnvironmentVariable(k)) {
      val parts    = formerValue.split(CustomWindowsEnvVarUpdater.windowsPathSeparator)
      val isInList = parts.contains(v)
      if (isInList) {
        val newValue = parts.filter(_ != v)
        if (newValue.isEmpty)
          clearEnvironmentVariable(k)
        else
          setEnvironmentVariable(
            k,
            newValue.mkString(CustomWindowsEnvVarUpdater.windowsPathSeparator)
          )
        setSomething = true
      }
    }

    setSomething
  }

}

object CustomWindowsEnvVarUpdater {

  private def getEnvVarScript(name: String): String =
    s"""[Environment]::GetEnvironmentVariable("$name", "User")
       |""".stripMargin
  private def setEnvVarScript(name: String, value: String): String =
    // FIXME value might need some escaping here
    s"""[Environment]::SetEnvironmentVariable("$name", "$value", "User")
       |""".stripMargin
  private def clearEnvVarScript(name: String): String =
    // FIXME value might need some escaping here
    s"""[Environment]::SetEnvironmentVariable("$name", $$null, "User")
       |""".stripMargin

  private def windowsPathSeparator: String =
    ";"

}
