package de.sciss.fscape
package tests

import de.sciss.fscape.Ops._
import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object IfThenElseGESimpleTest extends App {
//  showStreamLog = true

  val g = Graph {
    import graph._
//    (0: GE).poll(0, "zero")
    val p1: GE = 0
    val dc1 = DC(1)
    val out = If (p1) Then {
      dc1
//      DC(1)
    } Else {
      (1234: GE).poll(0, "test")
      2: GE // DC(2)
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
