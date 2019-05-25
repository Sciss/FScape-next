package de.sciss.lucre.swing

import de.sciss.lucre.expr.Context
import de.sciss.lucre.stm.UndoManager
import de.sciss.lucre.synth.InMemory
import de.sciss.synth.proc.{ExprContext, Universe}

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
    implicit val sys: S = InMemory()

    val view = sys.step { implicit tx =>
      implicit val u    : Universe    [S] = Universe.dummy
      implicit val undo : UndoManager [S] = UndoManager()
      implicit val ctx  : Context     [S] = ExprContext[S]()
      g.expand[S]
    }
    view.component
  }
}
