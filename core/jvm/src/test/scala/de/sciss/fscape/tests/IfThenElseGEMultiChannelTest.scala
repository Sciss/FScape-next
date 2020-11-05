package de.sciss.fscape.tests

import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.{GE, Graph, graph, stream}

import scala.swing.Swing

object IfThenElseGEMultiChannelTest extends App {
  val g = Graph {
    import graph._
    val tempIn         = new java.io.File("/data/projects/Almat/events/data-to-process2017/lecture_perf/schwaermen-frames-in/frame-%d.jpg")
    val numFramesIn    = 10 // 500
    val resampleIn     = 2: GE // "resample-in" .attr(1)
    val videoIn0  = ImageFileSeqIn(template = tempIn.toURI, numChannels = 3, indices =
      ArithmSeq(1, 1, numFramesIn)) // .out(0)

    val wIn       = 1920
    val hIn       = 1080

    val videoIn1  = videoIn0 // .take(wIn * hIn * 2)
//    Length(videoIn1).poll(0, "len-in")

    val videoIn   = If (resampleIn sig_== 1) Then {
      (0: GE).poll(0, "resampleIn is 1")
      videoIn1
    } Else {
      (0: GE).poll(0, "resampleIn is not 1")
      val res = ResampleWindow(videoIn1, size = wIn*hIn,
        factor = resampleIn, minFactor = resampleIn,
        rollOff = 0.9, kaiserBeta = 12, zeroCrossings = 15
      )
      // Length(res).poll(0, "res")
      res
    }

    Length(videoIn).poll(0, "len")
  }

  val config        = stream.Control.Config()
  config.blockSize  = 1024
  config.useAsync   = false // for debugging
  val ctrl          = stream.Control(config)

  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}
