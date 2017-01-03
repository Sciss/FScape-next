package de.sciss.fscape

import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.expr.IntObj
import de.sciss.lucre.synth.InMemory
import de.sciss.synth.proc.WorkspaceHandle

object OutputTest extends App {
  implicit val cursor = InMemory()
  type S              = InMemory

  cursor.step { implicit tx =>
    val f = FScape[S]
    val g = Graph {
      import graph._
      import lucre.graph._
      val value = WhiteNoise(100).take(100000000).last
      MkInt("out", value)
    }
    val out = f.outputs.add("out", IntObj)
    f.graph() = g
    assert(out.value.isEmpty)
    out.changed.react { implicit tx => upd =>
      println(s"Value is now ${upd.value}")
      sys.exit()
    }

    import WorkspaceHandle.Implicits.dummy
    f.run()

    new Thread {
      override def run(): Unit = Thread.sleep(Long.MaxValue)
      start()
    }
  }
}