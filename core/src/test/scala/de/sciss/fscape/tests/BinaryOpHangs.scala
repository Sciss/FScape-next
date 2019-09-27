package de.sciss.fscape.tests

import de.sciss.fscape
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.{Graph, graph, stream}

import scala.swing.Swing

object BinaryOpHangs extends App {
  val sz: Int = 1024
  val g = Graph {
    import graph._
    val pre     = Line(0.0, 0.0, sz)
    val A       = pre.take(sz)
    val B       = Line(0.0, 0.0, sz + 1)
    val C       = A * B
    Length(C).poll(0, "C")
    val D       = C :+ 0.0
    val E       = pre ++ D
    DebugSink(E)
  }

  val config        = stream.Control.Config()
  config.useAsync   = false
  config.blockSize  = sz
  val ctrl          = stream.Control(config)

  fscape.showStreamLog = true
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}