package de.sciss.fscape

import java.io.{ByteArrayOutputStream, PrintStream}

trait CaptureConsoleOutput {
  def captureConsole(body: => Any): String =
    captureConsoleWith(body)._2

  def captureConsoleWith[A](body: => A): (A, String) = {
    val baos  = new ByteArrayOutputStream()
    val os    = new PrintStream(baos)
    val res   = Console.withOut(os) {
      body
    }
    os.close()
    (res, baos.toString("UTF-8"))
  }
}
