package de.sciss.fscape

import de.sciss.kollflitz.Vec
import de.sciss.numbers.Implicits._

import scala.annotation.tailrec
import scala.concurrent.Promise

class WindowApplySpec extends UGenSpec {
  "The WindowApply UGen" should "work as intended" in {
    for {
      n     <- List(0, 10, 31, 32, 33)
      wSzSq <- List(1, 10, 31, 32, 33).map(_ :: Nil) ++ List(2 :: 3 :: Nil)
      idxSq <- List(-1, 0, 2, 11).map(_ :: Nil) ++ List(1 :: 2 :: Nil)
      mode  <- 0 to 3
    } {
      val p = Promise[Vec[Int]]()
      val g = Graph {
        import graph._
        val gen   = ArithmSeq(3, length = n)
        // gen.poll(1, "gen")
        val wSz   = ValueIntSeq(wSzSq: _*)
        val idx   = ValueIntSeq(idxSq: _*)
        val det   = WindowApply(gen, size = wSz, index = idx, mode = mode)
        DebugIntPromise(det, p)
      }

      runGraph(g, 32)

      assert(p.isCompleted)
      val res = getPromiseVec(p)

      val inSq = 3 until (3 + n)

      @tailrec
      def loop(i: Int, wi: Int, res: Vec[Int]): Vec[Int] =
        if (i >= n) res
        else {
          val wSz   = wSzSq(wi min (wSzSq.size - 1))
          val win   = inSq.slice(i, i + wSz).padTo(wSz, 0)
          val idx0  = idxSq(wi min (idxSq.size - 1))
          val x = if (mode == 3) {
            if (idx0 < 0 || idx0 >= wSz) 0 else win(idx0)
          } else {
            val idx = mode match {
              case 0 => idx0.clip(0, wSz - 1)
              case 1 => idx0.wrap(0, wSz - 1)
              case 2 => idx0.fold(0, wSz - 1)
            }
            win(idx)
          }
          loop(i = i + wSz, wi = wi + 1, res = res :+ x)
        }

      val exp = loop(i = 0, wi = 0, res = Vector.empty)

      assert (res === exp, s"n $n, wSzSq $wSzSq, idxSq $idxSq, mode $mode")
    }
  }
}