package de.sciss.fscape

import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object IfThenDebug extends App {
  showStreamLog = true

  val g = Graph {
    import graph._
    val sig0    = ArithmSeq(length = 4000) // DC(1234).take(4000)
    val videoIn = If (1: GE) Then {
//      (0: GE).poll(0, "resampleIn is 1")
      sig0
    } Else {
//      (0: GE).poll(0, "resampleIn is NOT 1")
      sig0 + 1
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
