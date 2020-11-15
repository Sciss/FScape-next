package de.sciss.lucre.swing

import de.sciss.lucre.expr.Context
import de.sciss.lucre.edit.UndoManager
import de.sciss.lucre.synth.InMemory
import de.sciss.proc.{ExprContext, Universe}

import scala.swing.Component

object AudioFileInOutTest extends AppLike {
  protected def mkView(): Component = {
    import graph._
    val g = Graph {
      val in = AudioFileIn()
      FlowPanel(
        Label("Input:"), in
      )
    }

    type              S = InMemory
    type              T = InMemory.Txn
    implicit val sys: S = InMemory()

    val view = sys.step { implicit tx =>
      implicit val u    : Universe    [T] = Universe.dummy
      implicit val undo : UndoManager [T] = UndoManager()
      implicit val ctx  : Context     [T] = ExprContext[T]()
      g.expand[T]
    }
    view.component
  }
}
