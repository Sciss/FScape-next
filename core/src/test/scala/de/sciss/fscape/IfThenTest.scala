package de.sciss.fscape

import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object IfThenTest extends App {
  val g = Graph {
    import graph._
    DC(0).take(10000).poll(0, "zero")
    val p1: GE = 1
    val p2: GE = 0
    val p3: GE = 1
    If (p1) Then {
      DC(1).take(20000)poll(0, "one")
      If (p2) Then {
        DC(2).take(30000)poll(0, "two")
      } ElseIf (p3) Then {
        DC(3).take(40000)poll(0, "three")
      }
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
