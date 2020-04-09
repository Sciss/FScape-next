package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class TakeRightSpec extends UGenSpec {
  "The TakeRight UGen" should "work as intended" in {
    for {
      inLen   <- Seq(0, 1, 10, 100, 300, 500, 1024, 1025)
      takeLen <- Seq(-1, 0, 1, 10, 100, 200, 600, 1024, 1025, 2000)
    } {
      val p = Promise[Vec[Int]]()
      val g = Graph {
        import graph._
        val in  = ArithmSeq(start = 1, length = inLen)
        val dr  = in.takeRight(takeLen)
        DebugIntPromise(dr, p)
      }

      val info = s"inLen $inLen, takeLen $takeLen"
      // println(info)
      runGraph(g, 1024)

      assert(p.isCompleted)
      val res     = getPromiseVec(p)
      val inSq    = if (inLen < 1) Vector.empty else (1 to inLen).toVector
      val exp     = inSq.takeRight(takeLen)
      assert (res === exp, info)
    }
  }
}