package tachiyomix.compiler

import javax.annotation.processing.Messager
import javax.tools.Diagnostic

internal class Logger(private val messager: Messager) {

  fun d(message: String?) {
    messager.printMessage(Diagnostic.Kind.NOTE, message)
  }

  fun w(message: String?) {
    messager.printMessage(Diagnostic.Kind.WARNING, message)
  }

  fun e(message: String?) {
    messager.printMessage(Diagnostic.Kind.ERROR, message)
  }

}
