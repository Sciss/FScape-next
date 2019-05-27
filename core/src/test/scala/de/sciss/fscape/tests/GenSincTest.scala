package de.sciss.fscape.tests

import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.{Graph, graph, stream}

import scala.swing.Swing

object GenSincTest extends App {
  val g = Graph {
    import graph._
    val sz = 1024
    val gen = GenWindow(size = sz, shape = GenWindow.Sinc, param = 0.01)
    Plot1D(gen, size = sz, label = "sin(x)/x")
  }

  val ctrl = stream.Control()

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  ctrl.run(g)
}