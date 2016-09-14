package de.sciss.fscape

import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.synth.InMemory
import de.sciss.synth.proc
import de.sciss.synth.proc.Action.Universe
import de.sciss.synth.proc.WorkspaceHandle

object OnCompleteTest extends App {
  implicit val cursor = InMemory()
  type S              = InMemory

  val body = new proc.Action.Body {
    def apply[T <: Sys[T]](universe: Universe[T])(implicit tx: T#Tx): Unit =
      tx.afterCommit {
        println(s"Completed: ${universe.value}")
        sys.exit()
      }
  }

  val fH = cursor.step { implicit tx =>
    val f = FScape[S]
    val g = Graph {
      import graph.{AudioFileOut => _, _}
      import lucre.graph._
      val sig = Line(0, 1, len = 100000000).sqrt
      sig.head.poll(0, "head")
      sig.last.poll(0, "last")
      OnComplete("action")
    }
    proc.Action.registerPredef("bang", body)
    val a = proc.Action.predef[S]("bang")
    f.attr.put("action", a)
    f.graph() = g
    import WorkspaceHandle.Implicits.dummy
    f.run()
  }
}