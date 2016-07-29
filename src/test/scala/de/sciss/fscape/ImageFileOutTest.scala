package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object ImageFileOutTest extends App {
  val width   = 1024
  val height  = 768

  val g = Graph {
    import graph._
    val xSin  = SinOsc(Seq[GE](0.5/width, 1.0/width, 1.5/width)).abs
    val ySin  = SinOsc(0.5/(height * width))
    val amp   = xSin * ySin
    val spec  = ImageFile.Spec(width = width, height = height, numChannels = 3 /* 1 */,
      fileType = ImageFile.Type.JPG, sampleFormat = ImageFile.SampleFormat.Int8,
      quality = 100)
    val f     = userHome / "Documents" / "temp" / "test.jpg"
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