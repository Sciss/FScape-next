package de.sciss.fscape

import de.sciss.fscape.Ops._
import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class SlidingWindowPercentileSpec extends UGenSpec {
  "The SlidingWindowPercentile UGen" should "work as intended" in {
    val p = Promise[Vec[Int]]()
    val wSz  = 4
    val mLen = 3
    val inSq = Vector(
       4, 13, 10,  9,   //  4, 13, 10,  9
       8, 16, 23, 10,
       7,  0,  8, 23,   //  7, 13, 10, 10
      22, 26,  2,  5,   //  8, 16,  8, 10
      17, 19, 16, 10,   // 17, 19,  8, 10
      13,  2, 13, 23,   // 17, 19, 13, 10
       8, 21, 24,  8,   // 13, 19, 16, 10
       1, 19, 22, 26,   //  8, 19, 22, 23
       7, 17, 15, 27,   //  7, 19, 22, 26
      29,  3,  6, 15,   //  7, 17, 15, 26
      22, 14,  0, 25,   // 22, 14,  6, 25
      17, 24, 23,  4    // 22, 14,  6, 15
    )
    val g = Graph {
      import graph._
      val in = ValueIntSeq(inSq: _*)
      val m = SlidingWindowPercentile(in, winSize = wSz, medianLen = mLen)
      val d = m.drop(wSz * (mLen - 1))
      // d.poll(1, "out")
      DebugIntPromise(d, p)
    }

    runGraph(g)

    assert(p.isCompleted)
    val res = getPromiseVec(p)

    def median(in: Vec[Int]): Int = {
      val inS = in.sorted
      inS(inS.size/2)
    }

    val exp: Vec[Int] = inSq.grouped(wSz).toVector.transpose.map { v =>
      v.sliding(mLen).map(median).toVector
    } .transpose.flatten

    assert (res === exp)
  }
}