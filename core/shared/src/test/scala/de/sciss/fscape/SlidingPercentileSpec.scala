package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class SlidingPercentileSpec extends UGenSpec {
  "The SlidingPercentile UGen" should "work as intended" in {
    val mLen = 3
    val inSq = Vector(5, 10, 12, 9, 12, 16, 25, 12, 20, 15, 11, 12, 13, 24, 28, 24, 4, 18, 6, 0)
    for {
      frac <- Seq(0.0, 0.5, 1.0)
    } {
      val p = Promise[Vec[Int]]()
      val g = Graph {
        import graph._
        val in = ValueIntSeq(inSq: _*)
        val m = SlidingPercentile(in, len = mLen, frac = frac)
        val d = m.drop(mLen - 1)
        DebugIntPromise(d, p)
      }

      runGraph(g)

      assert(p.isCompleted)
      val res = getPromiseVec(p)

      def perc(in: Vec[Int]): Int = {
        val inS = in.sorted
        inS(((inS.size - 1) * frac).toInt)
      }

      val exp: Vec[Int] = inSq.sliding(mLen).map(perc).toVector

      assert (res === exp)
    }
  }
}