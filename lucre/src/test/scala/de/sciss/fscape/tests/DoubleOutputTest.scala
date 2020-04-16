package de.sciss.fscape
package tests

import de.sciss.file._
import de.sciss.filecache.Limit
import de.sciss.fscape.lucre.FScape.Output
import de.sciss.fscape.lucre.{Cache, FScape}
import de.sciss.lucre.expr.DoubleObj
import de.sciss.lucre.synth.InMemory
import de.sciss.synth.proc.{GenView, Universe}

import scala.concurrent.stm.Ref
import scala.util.{Failure, Success}

object DoubleOutputTest extends App {
  type S                  = InMemory
  implicit val cursor: S  = InMemory()

  FScape.init()
  GenView.addFactory(FScape.genViewFactory())
  val folder = userHome / "Documents" / "temp" / "fscape_test" // File.createTemp(directory = true)
  folder.mkdir()
  Cache.init(folder = folder, capacity = Limit())

  cursor.step { implicit tx =>
    val f = FScape[S]
    val g = Graph {
      import graph._
      import lucre.graph._
      1.poll(0, label = "rendering")
      val value = WhiteNoise(100).take(100000000).last
      MkDouble("out-1", value)
      MkDouble("out-2", value + 1)
    }
    val out1 = f.outputs.add("out-1", DoubleObj)
    val out2 = f.outputs.add("out-2", DoubleObj)
    f.graph() = g

    val count = Ref(0)

    implicit val universe: Universe[S] = Universe.dummy

    def mkView(out: Output[S], idx: Int): GenView[S] = {
      val view = GenView(out)

      import de.sciss.lucre.stm.TxnLike.peer
      view.reactNow { implicit tx => upd =>
        if (upd.isComplete) {
          view.value.foreach { value =>
            value match {
              case Success(v)  =>
                println(s"Value ${idx + 1} is now $v")
              case Failure(ex) =>
                println(s"Value ${idx + 1} failed:")
                ex.printStackTrace()
            }
            if (count.transformAndGet(_ + 1) == 2) tx.afterCommit(sys.exit())
          }
        }
      }
      view
    }

    /* val view1 = */ mkView(out1, idx = 0)
    /* val view2 = */ mkView(out2, idx = 1)

    new Thread {
      override def run(): Unit = Thread.sleep(Long.MaxValue)
      start()
    }
  }
}