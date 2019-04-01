package de.sciss.lucre.swing

import de.sciss.lucre.expr.ExOps
import de.sciss.lucre.stm.{InMemory, Workspace}

import scala.swing.Component

object AudioFileInOutTest extends AppLike {
  protected def mkView(): Component = {
    import ExOps._
    import graph._
    val g = Graph {
      val in = AudioFileIn()
      FlowPanel(
        Label("Input:"), in
      )
    }

    type              S = InMemory
    implicit val sys: S = InMemory()
    import Workspace.Implicits._

    val view = sys.step { implicit tx =>
      g.expand[S]()
    }
    view.component
  }
}
