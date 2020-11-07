package de.sciss.fscape

import de.sciss.fscape.Ops._
import de.sciss.kollflitz.Vec
import de.sciss.numbers

import scala.annotation.tailrec
import scala.concurrent.Promise

class ARCWindowSpec extends UGenSpec {
  "The ARCWindow UGen" should "work as intended" in {
    val lag = 0.8
    val mod = 99
    for {
      winSq   <- Seq(0, 1, 10, 100, 511, 512, 513).map(n0 => n0 :: Nil) :+ Seq(511, 512, 513)
      lo      <- Seq(-1.0, 0.0, 1.0)
      hiSq    <- Seq(-1.0 :: Nil, -0.5 :: Nil, 0.0 :: Nil, 0.5 :: Nil, 2.0 :: Nil, Seq(2.0, 1.5, 1.0))
    } {
      val p = Promise[Vec[Double]]()
      val n = winSq.sum * 4
      val g = Graph {
        import graph._
        val win: GE = winSq match {
          case x :: Nil => x
          case xs       => xs.map(ConstantI(_): GE).reduce(_ ++ _)
        }
        val in  = ArithmSeq(start = 0.0, step = 1.0, length = n) % mod
        val hi: GE = hiSq match {
          case x :: Nil => x
          case xs       => xs.map(ConstantD(_): GE).reduce(_ ++ _)
        }
        val d   = ARCWindow(in, size = win, lo = lo, hi = hi, lag = lag)
        DebugDoublePromise(d, p)
      }

      runGraph(g, 512)

      assert(p.isCompleted)
      val res         = p.future.value.get.get
      val inSq        = Vector.tabulate(n)(_.toDouble % mod)

      @tailrec
      def loop(wi: Int, rem: Vec[Double], min: Double, max: Double, res: Vec[Double]): Vec[Double] =
        if (rem.isEmpty) res else {
          val winSize     = winSq (wi.min(winSq.size - 1))
          val hi          = hiSq  (wi.min(hiSq .size - 1))
          val (in, rem1)  = rem.splitAt(winSize)
          val minI        = in.min
          val maxI        = in.max
          val min1        = if (wi == 0) minI else min * lag + minI * (1.0 - lag)
          val max1        = if (wi == 0) maxI else max * lag + maxI * (1.0 - lag)
          import numbers.Implicits._
          val out         = if (min1 == max1) in.map(_ => lo) else in.map(_.linLin(min1, max1, lo, hi))
          loop(wi = wi + 1, rem = rem1, min = min1, max = max1, res = res ++ out)
        }

      val exp = loop(wi = 0, rem = inSq, min = 0.0, max = 0.0, res = Vector.empty)

      difOk(res, exp, s"n $n, lo $lo, hiSq $hiSq")
    }
  }
}