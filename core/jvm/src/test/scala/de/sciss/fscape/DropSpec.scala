package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class DropSpec extends UGenSpec {
  "The Drop UGen" should "work as intended" in {
    for {
      inLen   <- Seq(0, 1, 10, 100, 300, 500, 1024, 1025)
      dropLen <- Seq(-1, 0, 1, 10, 100, 200, 600, 1024, 1025, 2000)
    } {
      val p = Promise[Vec[Int]]()
      val g = Graph {
        import graph._
        val in  = ArithmSeq(start = 1, length = inLen)
        val dr  = in.drop(dropLen)
        DebugIntPromise(dr, p)
      }

      val info = s"inLen $inLen, dropLen $dropLen"
      // println(info)
      runGraph(g, 1024)

      assert(p.isCompleted)
      val res     = getPromiseVec(p)
      val inSq    = if (inLen < 1) Vector.empty else (1 to inLen).toVector
      val exp     = inSq.drop(dropLen)
      assert (res === exp, info)
    }
  }
}