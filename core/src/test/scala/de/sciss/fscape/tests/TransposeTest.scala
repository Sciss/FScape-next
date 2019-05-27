package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.{Graph, graph, stream}

import scala.swing.Swing

object TransposeTest extends App {
  val width   = 1920 // 1024
  val height  = 1080 // 768
  val fIn     = userHome / "Documents" / "temp" / "test.jpg"
  val fOut    = userHome / "Documents" / "temp" / "test-rot.jpg"

  val g = Graph {
    import graph._
    val in    = ImageFileIn(file = fIn, numChannels = 3)
//    val xSin  = SinOsc(Seq[GE](0.5/width, 1.0/width, 1.5/width)).abs
//    val ySin  = SinOsc(0.5/(height * width))
//    val in    = xSin * ySin

    val sig0  = TransposeMatrix(in = in, rows = height, columns = width)
    val sig   = ReverseWindow(sig0, size = height * width)
    val spec  = ImageFile.Spec(width = height, height = width, numChannels = 3,
      fileType = ImageFile.Type.JPG, sampleFormat = ImageFile.SampleFormat.Int8,
      quality = 100)
    ImageFileOut(file = fOut, spec = spec, in = sig)
  }

  val config  = stream.Control.Config()
  config.blockSize = 1024   // exposes problem
  val ctrl    = stream.Control(config)
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}