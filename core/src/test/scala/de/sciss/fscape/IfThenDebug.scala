package de.sciss.fscape

import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object IfThenDebug extends App {
  showStreamLog = true

  val g = Graph {
    import graph._
    val fIn         = new java.io.File("/data/projects/Almat/events/data-to-process2017/lecture_perf/schwaermen-frames-in/frame-1.jpg")
    val resampleIn  = 1: GE // "resample-in" .attr(1)

    val videoIn0  = ImageFileIn(fIn, numChannels = 3).out(0).take(4000)

    val videoIn   = If (resampleIn sig_== 1) Then {
      (0: GE).poll(0, "resampleIn is 1")
      videoIn0
    } Else {
      (0: GE).poll(0, "resampleIn is NOT 1")
      videoIn0 + 1
    }

    Length(videoIn.out(0)).poll(0, "len")
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
