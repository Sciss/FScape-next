package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.{Graph, graph, stream}

import scala.swing.Swing

object TransposeTest extends App {
  val width   = 2048 // 1920 // 1024
  val height  = 1535 // 1080 // 768
  val fIn     = userHome / "Pictures" / "naya" / "18237862_10156159758633065_8364916541260985254_o.jpg"
  val fOut    = userHome / "Documents" / "test-rot.jpg"

  val g = Graph {
    import graph._
    val in    = ImageFileIn(file = fIn.toURI, numChannels = 3)
//    val xSin  = SinOsc(Seq[GE](0.5/width, 1.0/width, 1.5/width)).abs
//    val ySin  = SinOsc(0.5/(height * width))
//    val in    = xSin * ySin

    val sig0  = TransposeMatrix(in = in, rows = height, columns = width)
    val sig   = ReverseWindow(sig0, size = height * width)
    val spec  = ImageFile.Spec(width = height, height = width, numChannels = 3,
      fileType = ImageFile.Type.JPG, sampleFormat = ImageFile.SampleFormat.Int8,
      quality = 100)
    ImageFileOut(file = fOut.toURI, spec = spec, in = sig)
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