package de.sciss.fscape

import de.sciss.kollflitz.Vec
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.Duration
import scala.util.Success

class ResizeWindowSpec extends FlatSpec with Matchers {
  "The ResizeWindow UGen" should "work as intended" in {
    def variant(numIn: Int, size: Int, num: Int, expected: Vec[Int]): Unit = {
      val p   = Promise[Vec[Int]]()
      val g = Graph {
        import graph._
        val inLen       = 385
        val in          = ArithmSeq().take(inLen)
        val maxWinSz    = 56
        val winSzInSq   = Seq(56, 36, 59, 26, 18, 49, 55, 41, 45)
        assert (winSzInSq.sum == inLen)
        val winSzIn: GE = winSzInSq.map(i => i: GE).reduce(_ ++ _)
        val winSzOut = winSzIn.min(maxWinSz)
        val dStop = winSzOut - winSzIn
        dStop.poll(1, "dStop") // (0, 0, -3, 0, 0, 0, 0, 0, 0) -- correct
        // winSzIn.poll(1, "winSzIn")
        val out  = ResizeWindow(in, winSzIn, stop = dStop)

        Length(in ).poll("in .length") // 385 -- correct
        Length(out).poll("out.length") // 392 -- wrong
        ??? // DebugIntPromise(r, p)
      }

      val cfg = stream.Control.Config()
      cfg.blockSize = 128
      val ctl = stream.Control(cfg)
      ctl.run(g)
      Await.result(ctl.status, Duration.Inf)

      assert(p.isCompleted)
      val res = p.future.value.get
      assert (res === Success(expected))
    }

    variant(numIn = 2, size = 1, num = 4, expected = Vec.fill(4)(1) ++ Vec.fill(4)(2))
    variant(numIn = 2, size = 1, num = 1, expected = Vec.fill(1)(1) ++ Vec.fill(1)(2))
    variant(numIn = 2, size = 2, num = 4, expected = Vec.fill(4)(Vec(1, 2)).flatten)
    variant(numIn = 2, size = 2, num = 1, expected = Vec.fill(1)(Vec(1, 2)).flatten)
    variant(numIn = 2, size = 3, num = 4, expected = Vec.fill(4)(Vec(1, 2, 0)).flatten)
    variant(numIn = 2, size = 3, num = 1, expected = Vec.fill(1)(Vec(1, 2, 0)).flatten)
    variant(numIn = 10, size = 3, num = 2, expected = Vec(1,2,3,1,2,3,4,5,6,4,5,6,7,8,9,7,8,9,10,0,0,10,0,0))
    variant(numIn = 200, size = 7, num = 4, expected =
      (1 to 200).grouped(7).map(_.padTo(7, 0)).flatMap(xs => Vec.fill(4)(xs)).flatten.toVector)
  }
}