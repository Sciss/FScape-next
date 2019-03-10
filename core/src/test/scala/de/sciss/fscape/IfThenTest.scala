package de.sciss.fscape

import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object IfThenTest extends App {
  val g = Graph {
    import graph._
    DC(0).poll(0, "zero")
    val p1: GE = 1
    If (p1) Then {
      DC(1).poll(0, "one")
    }
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
