package de.sciss.fscape
package tests

import de.sciss.fscape.Ops._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.log.Level

import scala.swing.{Button, Frame, Swing}

object PollTest extends App {
  Swing.onEDT {
    new Frame {
      contents = Button("Run")(run())
      pack().centerOnScreen()

      override def closeOperation(): Unit = sys.exit()

      open()
    }
  }

  def run(): Unit = {
    val g = Graph {
      (1: GE).poll(0, "foo")
    }

    val cb = stream.Control.Config()
    cb.useAsync = false
    val config = cb.build
    Log.stream.level = Level.Debug
    implicit val ctrl: stream.Control = stream.Control(config)
    ctrl.run(g)
//    import config.executionContext
//    ctrl.status.foreach { _ =>
//      println("Terminating actor system...")
//      cb.actorSystem.terminate()
//      cb.actorSystem.whenTerminated.foreach { _ =>
//        println("...terminated")
//      }
//    }

    Swing.onEDT {
      SimpleGUI(ctrl)
    }

    println("Running.")
  }
}