package de.sciss.fscape

import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object ShutdownDebug extends App {
  lazy val g = Graph {
    import graph._
    val gen = DebugGen()
    val sig = DebugTake(gen)
    DebugOut(sig)
  }

  val config        = stream.Control.Config()
  config.useAsync   = false
  implicit val ctrl = stream.Control(config)
  showStreamLog     = true
  ctrl.run(g)


  Swing.onEDT {
    SimpleGUI(ctrl)
  }
}