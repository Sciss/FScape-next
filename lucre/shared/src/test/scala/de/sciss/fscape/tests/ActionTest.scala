package de.sciss.fscape.tests

import de.sciss.fscape.lucre.FScape
import de.sciss.fscape.stream.Cancelled
import de.sciss.fscape.{Graph, graph, lucre}
import de.sciss.lucre.synth.InMemory
import de.sciss.synth.proc
import de.sciss.synth.proc.Universe

import scala.util.{Failure, Success}

/** New test for invoking actions from an FScape graph.
  * What is not (yet?) implemented is a way to pass the
  * rendering instance into the action so that it could
  * be stopped.
  */
object ActionTest extends App {
  type S                  = InMemory
  type T                  = InMemory.Txn
  implicit val cursor: S  = InMemory()

  var r: FScape.Rendering[T] = _

  val fH = cursor.step { implicit tx =>
    val a = proc.Action[T]()
    a.graph() = proc.Action.Graph {
      import de.sciss.lucre.expr.graph._
      PrintLn("Bang!")
      // r.cancel()(stx)
    }

    val f = FScape[T]()
    val g = Graph {
      import graph._
      import lucre.graph._
      val sig   = Line(0, 1, length = 100000000) // WhiteNoise()
      val tr    = Trig(sig > 0.9) // sig >= 0 & sig < 0.0001
      Action(trig = tr, key = "action")
    }
    f.attr.put("action", a)
    f.graph() = g
    tx.newHandle(f)
  }

  cursor.step { implicit tx =>
    val f = fH()
    implicit val universe: Universe[T] = Universe.dummy
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