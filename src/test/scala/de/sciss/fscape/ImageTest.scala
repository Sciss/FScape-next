package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object ImageTest extends App {
  val width   = 1024
  val height  = 768

  val g = Graph {
    import graph._
    val xSin  = SinOsc(0.5/width ).abs
    val ySin  = SinOsc(0.5/(height * width))
    val amp   = xSin * ySin
    val spec  = ImageFileOut.Spec(width = width, height = height, numChannels = 1, sampleFormat = ImageFileOut.SampleFormat.Int16)
    val f     = userHome / "Documents" / "temp" / "test.png"
    ImageFileOut(file = f, spec = spec, in = amp)
  }

  val config = stream.Control.Config()
  config.blockSize  = 599   // a prime number unrelated to `width` and `height`, for testing
  config.useAsync   = false // for debugging
  val ctrl  = stream.Control(config)

//  showStreamLog = true

  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}