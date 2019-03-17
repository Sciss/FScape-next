package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object ImageFileInOutTest extends App {
  val baseDir = {
    val d1 = file("/data") / "temp"
    if (d1.isDirectory) d1 else userHome / "Documents" / "temp"
  }
//  val fIn     = baseDir / "test.jpg"
  val fIn     = userHome / "Pictures" / "2017-11-27-192106s.jpg"
  val fOut    = baseDir / "test-out.jpg"

  require (fIn.exists())

  val specIn = ImageFile.readSpec(fIn)
  import specIn.{width, height}

  val g = Graph {
    import graph._
    val in    = ImageFileIn(file = fIn, numChannels = 3)
    val sig   = in.pow(0.5) // 'gamma'
//    val sig   = in.clip(0.0, 1.0)
    val spec  = ImageFile.Spec(width = width, height = height, numChannels = 3 /* 1 */,
      fileType = ImageFile.Type.JPG, sampleFormat = ImageFile.SampleFormat.Int8,
      quality = 100)
    ImageFileOut(file = fOut, spec = spec, in = sig)
  }

  val config = stream.Control.Config()
  config.blockSize  = 1024 // 599   // a prime number unrelated to `width` and `height`, for testing
  config.useAsync   = false // for debugging
  val ctrl  = stream.Control(config)

  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}