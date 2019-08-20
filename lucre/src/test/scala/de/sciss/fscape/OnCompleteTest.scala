package de.sciss.fscape

import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.synth.InMemory
import de.sciss.synth.proc
import de.sciss.synth.proc.Universe

object OnCompleteTest extends App {
  type S                  = InMemory
  implicit val cursor: S  = InMemory()

  val body: proc.ActionRaw.Body = new proc.ActionRaw.Body {
    def apply[T <: Sys[T]](universe: proc.ActionRaw.Universe[T])(implicit tx: T#Tx): Unit =
      tx.afterCommit {
        println(s"Completed: ${universe.value}")
        sys.exit()
      }
  }

  new Thread {
    override def run(): Unit = Thread.sleep(Long.MaxValue)
    start()
  }

  cursor.step { implicit tx =>
    val f = FScape[S]
    val g = Graph {
      import graph.{AudioFileOut => _, _}
      import lucre.graph._
      val sig = Line(0, 1, length = 100000000).sqrt
      sig.head.poll(0, "head")
      sig.last.poll(0, "last")
      OnComplete("action")
    }
    proc.ActionRaw.registerPredef("bang", body)
    val a = proc.ActionRaw.predef[S]("bang")
    f.attr.put("action", a)
    f.graph() = g
    implicit val universe: Universe[S] = Universe.dummy
    f.run()
  }
}