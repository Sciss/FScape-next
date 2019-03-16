package de.sciss.fscape

import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object IfThenElseGEMultiChannelTest extends App {
  val g = Graph {
    import graph._
    val tempIn         = new java.io.File("/data/projects/Almat/events/data-to-process2017/lecture_perf/schwaermen-frames-in/frame-%d.jpg")
    val numFramesIn    = 10 // 500
    val resampleIn     = 2: GE // "resample-in" .attr(1)
    val videoIn0  = ImageFileSeqIn(template = tempIn, numChannels = 3, indices =
      ArithmSeq(1, 1, numFramesIn)) // .out(0)

    val wIn       = 1920
    val hIn       = 1080

    val videoIn   = If (resampleIn sig_== 1) Then {
      (0: GE).poll(0, "resampleIn is 1")
      videoIn0
    } Else {
      (0: GE).poll(0, "resampleIn is not 1")
      ResampleWindow(videoIn0, size = wIn*hIn,
        factor = resampleIn, minFactor = resampleIn,
        rollOff = 0.9, kaiserBeta = 12, zeroCrossings = 15
      )
    }

    Length(videoIn).poll(0, "len")
  }

  val config      = stream.Control.Config()
  config.useAsync = false // for debugging
  val ctrl        = stream.Control(config)

  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}
