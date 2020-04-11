package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class SortWindowSpec extends UGenSpec {
  "The SortWindow UGen" should "work as intended" in {
    val p = Promise[Vec[Int]]()
    val g = Graph {
      import graph._
      val in0 = ArithmSeq(length = 10) % 6  // 0,  1,  2,  3,  4, 5,  0,  1,  2,  3
      val in  = ReverseWindow(in0, 5)       // 4,  3,  2,  1,  0, 3,  2,  1,  0,  5
      val sig = SortWindow(in, -in, 5)      // 0, -1, -2, -3, -4, 0, -1, -2, -3, -5
      DebugIntPromise(sig, p)
    }

    runGraph(g, 1024)

    val inSq = (0 until 10).map(_ % 6)
    val inW  = inSq.grouped(5).map(_.reverse)
    val exp  = inW.flatMap(in => (in zip in.map(-_)).sortBy(_._1).map(_._2)).toVector

    assert(p.isCompleted)
    val res = getPromiseVec(p)
    assert (res === exp)
  }
}