package de.sciss.fscape

import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object IfThenElseGETest extends App {
  val g = Graph {
    import graph._
    DC(0).take(10000).poll(0, "zero")
    val p1: GE = 1
    val out = If (p1) Then {
      DC(1)
    } Else {
      DC(2)
    }
    out.take(1000).poll(0, "out")
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
