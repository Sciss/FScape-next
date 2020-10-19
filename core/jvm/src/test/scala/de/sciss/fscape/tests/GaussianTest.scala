package de.sciss.fscape.tests

import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.{Graph, graph, stream}

import scala.swing.Swing

object GaussianTest extends App {
  val g = Graph {
    import graph._
    val sz1 = 512
    val sz2 = 256
    val gen = GenWindow(size = Concat(sz1, sz2), shape = Concat(GenWindow.Gauss, GenWindow.Triangle), param = 6)
    Plot1D(gen, size = sz1 + sz2, label = "Gauss")
  }

  val ctrl = stream.Control()

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  ctrl.run(g)
}