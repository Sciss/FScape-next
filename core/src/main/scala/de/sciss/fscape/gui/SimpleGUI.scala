package de.sciss.fscape.gui

import de.sciss.fscape.stream.{Cancelled, Control}

import scala.concurrent.ExecutionContext
import scala.swing.{Button, FlowPanel, Frame, Label, Swing}
import scala.util.{Failure, Success}

object SimpleGUI {
  def apply(ctrl: Control): Frame = {
    val txtCancelled = "Cancelled."
    val lbCancelled = new Label(txtCancelled)
    lbCancelled.preferredSize = lbCancelled.preferredSize
    lbCancelled.text = null

    var finished = false

    val ggCancel = Button("Cancel")(ctrl.cancel())
    val ggDump   = Button("Dump") {
      println(ctrl.stats)
      ctrl.debugDotGraph()
    }

    import ExecutionContext.Implicits.global
    ctrl.status.onComplete { tr =>
      Swing.onEDT {
        finished          = true
        ggCancel.enabled  = false
        lbCancelled.text  = tr match {
          case Success(())          => "Done."
          case Failure(Cancelled()) => txtCancelled
          case Failure(ex)          =>
            ex.printStackTrace()
            s"Error: ${ex.getCause}"
        }
      }
    }

    new Frame {
      title = "Control"

      contents = new FlowPanel(ggCancel, ggDump, lbCancelled)
      pack().centerOnScreen()
      open()

      override def closeOperation(): Unit = {
        if (finished) sys.exit()
      }
    }
  }
}