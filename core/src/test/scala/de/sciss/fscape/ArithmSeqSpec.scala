package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.annotation.tailrec
import scala.concurrent.Promise

class ArithmSeqSpec extends UGenSpec {
  "The ArithmSeq UGen" should "work as intended" in {
    for {
      lenSq   <- Seq(Seq(1)) // Seq(0, 1, 10, 100, 511, 512, 513).map(n0 => n0 :: Nil) :+ Seq(511, 512, 513)
      lo      <- Seq(1, 0, 1)
      stepSq  <- Seq(-1 :: Nil, 0 :: Nil, 2 :: Nil, Seq(2, 3, 1))
    } {
      val p = Promise[Vec[Int]]()
      val g = Graph {
        import graph._
        val stepGE  = asGE(stepSq)
        val lenGE   = asGE(lenSq)
        val in      = ArithmSeq(start = lo, step = stepGE, length = lenGE)
        DebugIntPromise(in, p)
      }

      runGraph(g, 512)

      assert(p.isCompleted)
      val res = p.future.value.get.get

      @tailrec
      def loop(wi: Int, res: Vec[Int]): Vec[Int] =
        if (wi == lenSq.size) res else {
          val len   = lenSq(wi)
          val step  = stepSq(wi min (stepSq.size - 1))
          val out   = Vector.tabulate(len)(i => i * step + lo)
          loop(wi = wi + 1, res = res ++ out)
        }

      val exp = loop(wi = 0, res = Vector.empty)
      assert (res === exp, s"lenSq $lenSq, lo $lo, stepSq $stepSq")
    }
  }
}