package de.sciss.fscape

import de.sciss.fscape.Ops._
import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class HilbertCurveSpec extends UGenSpec {
  "The HilbertCurve.From2D UGen" should "work as intended" in {
    val p = Promise[Vec[Int]]()
    val g = Graph {
      import graph._
      val n   = 4
      val in  = ArithmSeq(length = n*n)
      val x   = in % n
      val y   = (in / n).toInt
      val pos = HilbertCurve.From2D(n = n, x = x, y = y)
      DebugIntPromise(pos, p)
    }

    runGraph(g)

    val res = getPromiseVec(p)
    val exp = Seq(0, 1, 14, 15, 3, 2, 13, 12, 4, 7, 8, 11, 5, 6, 9, 10)
    assert (res === exp)
  }

  "The HilbertCurve.To2D UGen" should "work as intended" in {
    val p = Promise[Vec[Int]]()
    val g = Graph {
      import graph._
      val n   = 4
      val pos = ArithmSeq(length = n*n)
      val out = HilbertCurve.To2D(n = n, pos = pos)
      DebugIntPromise(out.x zip out.y, p)
    }

    runGraph(g)

    val resFlat = getPromiseVec(p)
    val res = resFlat.grouped(2).map(seq => (seq(0), seq(1))).toList
    val exp = Seq((0,0), (1,0), (1,1), (0,1), (0,2), (0,3), (1,3), (1,2),
                  (2,2), (2,3), (3,3), (3,2), (3,1), (2,1), (2,0), (3,0))
    assert (res === exp)
  }
}