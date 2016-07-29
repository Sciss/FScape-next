package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object ImageFileInOutTest extends App {
  val width   = 1024
  val height  = 768
  val fIn     = userHome / "Documents" / "temp" / "test.jpg"
  val fOut    = userHome / "Documents" / "temp" / "test-out.jpg"

  val g = Graph {
    import graph._
    val in    = ImageFileIn(file = fIn, numChannels = 3)
    val sig   = in.pow(1.4) // 'gamma'
    val spec  = ImageFileOut.Spec(width = width, height = height, numChannels = 3 /* 1 */,
      fileType = ImageFileOut.FileType.JPG, sampleFormat = ImageFileOut.SampleFormat.Int8,
      quality = 100)
    ImageFileOut(file = fOut, spec = spec, in = sig)
  }

  val config = stream.Control.Config()
  config.blockSize  = 599   // a prime number unrelated to `width` and `height`, for testing
  config.useAsync   = false // for debugging
  val ctrl  = stream.Control(config)

  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}