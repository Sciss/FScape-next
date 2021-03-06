package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.concurrent.Promise
import scala.util.Success

class ReverseWindowSpec extends UGenSpec {
  "The ReverseWindow UGen" should "work as intended" in {
    def variant(inLen: Int, winSize: Int, clump: Int): Unit = {
      val p   = Promise[Vec[Int]]()

      val inDataP     = if (inLen < 1) Vector.empty else 1 to inLen
      val inDataSq    = inDataP.grouped(winSize)
      val expected: Vector[Int] = inDataSq.flatMap { in0 =>
        val in1 = if (in0.size >= winSize) in0 else in0.padTo(winSize, 0)
        val szH   = in1.size / 2
        val szHC  = szH - (szH % clump)
        val szH1  = in1.size - szHC
        val g1    = in1         .grouped(clump)               .toVector.reverse.flatten
        val g2    = in1.reverse .grouped(clump).map(_.reverse).toVector.flatten
        val in2   = g2.take(szHC) ++ in1.slice(szHC, szH1) ++ g1.takeRight(szHC)
        in2
      } .toVector

      val g = Graph {
        import graph._
        val oneTwo  = ArithmSeq(start = 1, length = inLen)
        val r       = ReverseWindow(oneTwo, size = winSize, clump = clump)
        DebugIntPromise(r, p)
      }

      runGraph(g, 128)

      assert(p.isCompleted)
      val res = p.future.value.get
      assert (res === Success(expected), info)
    }

    for {
      inLen   <- List(0, 1, 2, 10, 128, 129, 200)
      winSz   <- List(1, 2, 9)
      clump   <- 1 to 4
    } {
      variant(inLen = inLen, winSize = winSz, clump = clump)
    }
  }
}