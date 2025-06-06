package scala.build.postprocessing

import org.objectweb.asm

import java.nio.file.{FileAlreadyExistsException, NoSuchFileException}

import scala.build.{Logger, Os, retry}

object AsmPositionUpdater {

  private class LineNumberTableMethodVisitor(
    lineShift: Int,
    delegate: asm.MethodVisitor
  ) extends asm.MethodVisitor(asm.Opcodes.ASM9, delegate) {
    override def visitLineNumber(line: Int, start: asm.Label): Unit =
      super.visitLineNumber(line + lineShift, start)
  }

  private class LineNumberTableClassVisitor(
    mappings: Map[String, (String, Int)],
    cw: asm.ClassWriter
  ) extends asm.ClassVisitor(asm.Opcodes.ASM9, cw) {
    private var lineShiftOpt                                      = Option.empty[Int]
    def mappedStuff                                               = lineShiftOpt.nonEmpty
    override def visitSource(source: String, debug: String): Unit =
      mappings.get(source) match {
        case None =>
          super.visitSource(source, debug)
        case Some((newSource, lineShift)) =>
          lineShiftOpt = Some(lineShift)
          super.visitSource(newSource, debug)
      }
    override def visitMethod(
      access: Int,
      name: String,
      descriptor: String,
      signature: String,
      exceptions: Array[String]
    ): asm.MethodVisitor = {
      val main = super.visitMethod(access, name, descriptor, signature, exceptions)
      lineShiftOpt match {
        case None            => main
        case Some(lineShift) => new LineNumberTableMethodVisitor(lineShift, main)
      }
    }
  }

  def postProcess(
    mappings: Map[String, (String, Int)],
    output: os.Path,
    logger: Logger
  ): Unit = {
    os.walk(output)
      .iterator
      .filter(os.isFile(_))
      .filter(_.last.endsWith(".class"))
      .foreach { path =>
        try retry()(logger) {
            val is                = os.read.inputStream(path)
            val updateByteCodeOpt =
              try retry()(logger) {
                  val reader  = new asm.ClassReader(is)
                  val writer  = new asm.ClassWriter(reader, 0)
                  val checker = new LineNumberTableClassVisitor(mappings, writer)
                  reader.accept(checker, 0)
                  if checker.mappedStuff then Some(writer.toByteArray) else None
                }
              catch {
                case e: ArrayIndexOutOfBoundsException =>
                  e.getStackTrace.foreach(ste => logger.debug(ste.toString))
                  logger.log(s"Error while processing ${path.relativeTo(Os.pwd)}: $e.")
                  logger.log("Are you trying to run too many builds at once? Trying to recover...")
                  None
              }
              finally is.close()
            for (b <- updateByteCodeOpt) {
              logger.debug(s"Overwriting ${path.relativeTo(Os.pwd)}")
              os.write.over(path, b)
            }
          }
        catch {
          case e: (NoSuchFileException | FileAlreadyExistsException | ArrayIndexOutOfBoundsException) =>
            logger.debugStackTrace(e)
            logger.log(s"Error while processing ${path.relativeTo(Os.pwd)}: $e")
            logger.log("Are you trying to run too many builds at once? Trying to recover...")
        }
      }
  }
}
