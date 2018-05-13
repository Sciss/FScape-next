/*
 *  SimpleGUI.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.gui

import de.sciss.fscape.stream.{Cancelled, Control}
import de.sciss.numbers

import scala.concurrent.ExecutionContext
import scala.swing.{BorderPanel, Button, FlowPanel, Frame, Label, ProgressBar, Swing}
import scala.util.{Failure, Success}

object SimpleGUI {
  def apply(ctrl: Control): SimpleGUI = {
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
    val ggProg    = new ProgressBar
    ggProg.max    = 250

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

    val f = new Frame {
      title = "Control"

      contents = new BorderPanel {
        add(new FlowPanel(ggCancel, ggDump, lbCancelled), BorderPanel.Position.Center)
        add(ggProg, BorderPanel.Position.South)
      }
      pack().centerOnScreen()
      open()

      override def closeOperation(): Unit = {
        if (finished) sys.exit()
      }
    }

    new Impl(f, ggProg)
  }

  private final class Impl(val frame: Frame, ggProg: ProgressBar) extends SimpleGUI {
    private[this] var _prog = 0.0

    def progress: Double = _prog
    def progress_=(value: Double): Unit = if (_prog != value) {
      _prog = value
      import numbers.Implicits._
      ggProg.value = value.linLin(0, 1, 0, 250).toInt
    }
  }
}
trait SimpleGUI {
  def frame: Frame
  var progress: Double
}