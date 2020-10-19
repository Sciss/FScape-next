package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.concurrent.Promise
import scala.util.{Success, Try}

class FramesSpec extends UGenSpec {
  "The Frames and Indices UGens" should "work as specified" in {
    val lengths = List(
      0, 1, 10, 100, 1024, 1025, 10000
    )

    lengths.foreach { n =>
      val pFr   = Promise[Vec[Int]]()
      val pIdx  = Promise[Vec[Int]]()

      val g = Graph {
        import graph._

        val x   = DC(1).take(n)
        val fr  = Frames(x)
        val idx = x.indices
        DebugIntPromise(fr  , pFr )
        DebugIntPromise(idx , pIdx)
      }

      runGraph(g, 1024)

      assert(pFr.isCompleted)
      val resFr : Try[Vec[Int]] = pFr .future.value.get
      val resIdx: Try[Vec[Int]] = pIdx.future.value.get
      val expFr   = if (n == 0) Nil else 1 to n
      val expIdx  = if (n == 0) Nil else 0 until n
      assert (resFr  === Success(expFr  ))
      assert (resIdx === Success(expIdx ))
    }
  }
}