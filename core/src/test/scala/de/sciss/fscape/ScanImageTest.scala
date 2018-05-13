package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object ScanImageTest extends App {
  val width   = 1296
  val height  =  754
  val fIn     = file("/data/temp/test_mst.png")
  val fOut    = file("/data/temp/test_mst_osc.png")

  val g = Graph {
    import graph._
    val in          = ImageFileIn(file = fIn, numChannels = 3)
//    val period      = width
//    val numPeriods  = height
//    val freqN       = 1.0/period
//    val cx          = width/2
//    val cy          = height/2
//    val rx          = Line(start = 0, end = cx, length = period * numPeriods)
//    val ry          = Line(start = 0, end = cy, length = period * numPeriods)
//    val x           = SinOsc(freqN, phase = math.Pi/2) * rx + cx
//    val y           = SinOsc(freqN, phase = 0.0)       * ry + cy

    // we rotate the image by 180 degrees; shift by half a pixel to see the interpolation
    val x           = LFSaw(-1.0/width)           .linLin(-1, 1, 0, width ).roundTo(1) + 0.5
    val y           = LFSaw(-1.0/(width * height)).linLin(-1, 1, 0, height).roundTo(1) + 0.5
    val sig         = ScanImage(in, width = width, height = height, x = x, y = y, zeroCrossings = 0).max(0).min(1)
    val spec        = ImageFile.Spec(width = width, height = height, numChannels = 3,
      fileType = ImageFile.Type.PNG, sampleFormat = ImageFile.SampleFormat.Int8)
    ImageFileOut(file = fOut, spec = spec, in = sig)
  }

  val config = stream.Control.Config()
  config.useAsync = false
  val ctrl = stream.Control(config)

  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}