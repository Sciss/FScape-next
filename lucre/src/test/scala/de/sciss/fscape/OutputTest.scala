package de.sciss.fscape

import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.expr.IntObj
import de.sciss.lucre.synth.InMemory
import de.sciss.synth.proc.WorkspaceHandle

import scala.concurrent.stm.Ref

object OutputTest extends App {
  implicit val cursor = InMemory()
  type S              = InMemory

  cursor.step { implicit tx =>
    val f = FScape[S]
    val g = Graph {
      import graph._
      import lucre.graph._
      val value = WhiteNoise(100).take(100000000).last
      MkInt("out-1", value)
      MkInt("out-2", value + 1)
    }
    val out1 = f.outputs.add("out-1", IntObj)
    val out2 = f.outputs.add("out-2", IntObj)
    f.graph() = g
    assert(out1.value.isEmpty)
    assert(out2.value.isEmpty)

    val count = Ref(0)

    import de.sciss.lucre.stm.TxnLike.peer
    out1.changed.react { implicit tx => upd =>
      println(s"Value 1 is now ${upd.value}")
      if (count.transformAndGet(_ + 1) == 2) tx.afterCommit(sys.exit())
    }
    out2.changed.react { implicit tx => upd =>
      println(s"Value 2 is now ${upd.value}")
      if (count.transformAndGet(_ + 1) == 2) tx.afterCommit(sys.exit())
    }

    import WorkspaceHandle.Implicits.dummy
    val r = f.run()
    r.reactNow { implicit tx => state =>
      println(s"Rendering state: $state")
    }

    new Thread {
      override def run(): Unit = Thread.sleep(Long.MaxValue)
      start()
    }
  }
}