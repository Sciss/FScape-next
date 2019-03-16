package de.sciss.fscape

import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object IfThenDebug extends App {
  showStreamLog = true

  val g = Graph {
    import graph._
    val sig0  = ArithmSeq(length = 4000) // DC(1234).take(4000)
    val sig   = If (0: GE) Then {
      (0: GE).poll(1, "COND is 1")
      sig0
    } Else {
      (0: GE).poll(0, "COND is NOT 1")
      sig0 + 1
    }

    RunningSum(sig).last.poll(0, "sum")
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
