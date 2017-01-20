package de.sciss.fscape

import de.sciss.file.File
import de.sciss.filecache.Limit
import de.sciss.fscape.lucre.{Cache, FScape}
import de.sciss.fscape.lucre.FScape.Output
import de.sciss.lucre.expr.IntObj
import de.sciss.lucre.synth.InMemory
import de.sciss.synth.proc.{GenContext, GenView, WorkspaceHandle}

import scala.concurrent.stm.Ref

object OutputTest extends App {
  implicit val cursor = InMemory()
  type S              = InMemory

  FScape.init()
  GenView.addFactory(FScape.genViewFactory())
  val folder = File.createTemp(directory = true)
  Cache.init(folder = folder, capacity = Limit())

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

//    assert(out1.value.isEmpty)
//    assert(out2.value.isEmpty)

    val count = Ref(0)

    import WorkspaceHandle.Implicits.dummy
    implicit val genCtx = GenContext[S]

    def mkView(out: Output[S], idx: Int): GenView[S] = {
      val view = GenView(out)

      import de.sciss.lucre.stm.TxnLike.peer
      view.react { implicit tx => upd =>
        if (upd.isComplete) {
          view.value.foreach { value =>
            println(s"Value ${idx + 1} is now $value")
            if (count.transformAndGet(_ + 1) == 2) tx.afterCommit(sys.exit())
          }
        }
      }
      view
    }

    val view1 = mkView(out1, idx = 0)
    val view2 = mkView(out2, idx = 1)

//    val r = f.run()
//    r.reactNow { implicit tx => state =>
//      println(s"Rendering state: $state")
//    }

    new Thread {
      override def run(): Unit = Thread.sleep(Long.MaxValue)
      start()
    }
  }
}