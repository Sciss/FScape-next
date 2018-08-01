package de.sciss.fscape

import de.sciss.fscape.lucre.FScape
import de.sciss.fscape.stream.Cancelled
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.synth.InMemory
import de.sciss.synth.proc
import de.sciss.synth.proc.Action.Universe
import de.sciss.synth.proc.GenContext

import scala.util.{Failure, Success}

object ActionTest extends App {
  type S                  = InMemory
  implicit val cursor: S  = InMemory()

  var r: FScape.Rendering[S] = _

  val body: proc.Action.Body = new proc.Action.Body {
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
      val sig   = Line(0, 1, length = 100000000) // WhiteNoise()
      val tr    = sig > 0.9 // sig >= 0 & sig < 0.0001
      Action(trig = tr, key = "action")
    }
    proc.Action.registerPredef("bang", body)
    val a = proc.Action.predef[S]("bang")
    f.attr.put("action", a)
    f.graph() = g
    tx.newHandle(f)
  }

  import de.sciss.lucre.stm.WorkspaceHandle.Implicits.dummy

  cursor.step { implicit tx =>
    val f = fH()
    implicit val ctx: GenContext[S] = GenContext.apply
    r = f.run()
    r.reactNow { implicit tx => state =>
      println(s"Rendering: $state")
      if (state.isComplete) r.result.foreach {
        case Failure(Cancelled()) =>
          sys.exit()
        case Failure(ex) =>
          ex.printStackTrace()
          sys.exit(1)
        case Success(_) =>
          sys.exit()
      }
    }
  }
}