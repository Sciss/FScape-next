package de.sciss.fscape

import de.sciss.fscape.lucre.FScape
import de.sciss.fscape.lucre.FScape.Rendering
import de.sciss.fscape.stream.Cancelled
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.synth.InMemory
import de.sciss.synth.proc
import de.sciss.synth.proc.Action.Universe
import de.sciss.synth.proc.WorkspaceHandle

object ActionTest extends App {
  implicit val cursor = InMemory()
  type S              = InMemory

  var r: FScape.Rendering[S] = _

  val body = new proc.Action.Body {
    def apply[T <: Sys[T]](universe: Universe[T])(implicit tx: T#Tx): Unit = {
      tx.afterCommit(println("Bang!"))
      assert(tx.system == cursor)
      val stx = tx.asInstanceOf[S#Tx]
      r.cancel()(stx)
    }
  }

  val fH = cursor.step { implicit tx =>
    val f = FScape[S]
    val g = Graph {
      import graph.{AudioFileOut => _, _}
      import lucre.graph._
      val sig   = WhiteNoise()
      val tr    = sig >= 0 & sig < 0.0001
      Action(trig = tr, key = "action")
    }
    proc.Action.registerPredef("bang", body)
    val a = proc.Action.predef[S]("bang")
    f.attr.put("action", a)
    f.graph() = g
    tx.newHandle(f)
  }

  import WorkspaceHandle.Implicits.dummy

  cursor.step { implicit tx =>
    val f = fH()
    r = f.run()
    r.reactNow { implicit tx => state =>
      println(s"Rendering: $state")
      state match {
        case Rendering.Failure(Cancelled()) =>
          sys.exit()
        case Rendering.Failure(ex) =>
          ex.printStackTrace()
          sys.exit(1)
        case Rendering.Success =>
          sys.exit()
        case _ =>
      }
    }
  }
}