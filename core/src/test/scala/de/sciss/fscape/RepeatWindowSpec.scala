package de.sciss.fscape

import de.sciss.kollflitz.Vec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Success

class RepeatWindowSpec extends AnyFlatSpec with Matchers {
  "The RepeatWindow UGen" should "work as intended" in {
    def variant(numIn: Int, size: Int, num: Int, expected: Vec[Int]): Unit = {
      val p   = Promise[Vec[Int]]()
      val g = Graph {
        import graph._
        val oneTwo  = ArithmSeq(start = 1, length = numIn)
        val r       = RepeatWindow(oneTwo, size = size, num = num)
        DebugIntPromise(r, p)
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