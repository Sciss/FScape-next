package de.sciss.fscape

import de.sciss.kollflitz.Vec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.{Success, Try}

class FramesSpec extends AnyFlatSpec with Matchers {
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

      val cfg = stream.Control.Config()
      cfg.blockSize = 1024
      val ctl = stream.Control()
      ctl.run(g)
      Await.result(ctl.status, Duration.Inf)

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