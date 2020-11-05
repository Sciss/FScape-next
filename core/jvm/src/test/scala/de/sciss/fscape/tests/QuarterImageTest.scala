package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.{Graph, graph, stream}

import scala.swing.Swing

object QuarterImageTest extends App {
  val width   = 1024
  val height  = 768

  val g = Graph {
    import graph._
    val baseDir   = userHome / "Documents" / "projects" / "Eisenerz" / "image_work6"
    val template  = "frame-%d.jpg"
    val idxRange  = (276 to 628) .take(12) // .map(x => x: GE)
//    val numInput  = idxRange.size
    val width     = 3280
    val height    = 2464
    val img       = ImageFileIn(file = (baseDir / template.format(idxRange.head)).toURI, numChannels = 3)
    val half1     = ResizeWindow(img  , size = width, start = width/2, stop = 0)
    val half2     = ResizeWindow(half1, size = width / 2 * height, start = width / 2 * height / 2, stop = 0)
    val sig       = half2

    val spec  = ImageFile.Spec(width = width/2, height = height/2, numChannels = 3 /* 1 */,
      fileType = ImageFile.Type.JPG, sampleFormat = ImageFile.SampleFormat.Int8,
      quality = 100)
    val f     = userHome / "Documents" / "temp" / "test.jpg"
    ImageFileOut(file = f.toURI, spec = spec, in = sig)
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