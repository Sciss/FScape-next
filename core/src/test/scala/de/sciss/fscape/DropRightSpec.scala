package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.concurrent.Promise
import scala.util.Success

class DropRightSpec extends UGenSpec {
  "The DropRight UGen" should "work as intended" in {
    for {
      inLen   <- Seq(0, 1, 10, 100, 300, 500, 1024, 1025)
      dropLen <- Seq(-1, 0, 1, 10, 100, 200, 600, 1024, 1025, 2000)
    } {
      val p = Promise[Vec[Int]]()
      val g = Graph {
        import graph._
        val in  = ArithmSeq(start = 1, length = inLen)
        val dr  = in.dropRight(dropLen)
        DebugIntPromise(dr, p)
      }

      runGraph(g, 1024)

      assert(p.isCompleted)
      val res     = p.future.value.get
      val inSq    = if (inLen < 1) Vector.empty else (1 to inLen).toVector
      val exp     = inSq.dropRight(dropLen)
      assert (res === Success(exp), s"inLen $inLen, dropLen $dropLen")
    }
  }
}