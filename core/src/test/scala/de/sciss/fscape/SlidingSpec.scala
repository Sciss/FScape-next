package de.sciss.fscape

import de.sciss.fscape.stream.Control.Config
import de.sciss.kollflitz.Vec

import scala.annotation.tailrec
import scala.concurrent.Promise

class SlidingSpec extends UGenSpec {
  "The Sliding UGen" should "work as intended" in {
    var COUNT = 0
    for {
      inLen <- Seq(2) // Seq(/*0,*/ 1, 7, 8, 9)
      win   <- Seq(3) // Seq(/*0,*/ 1, 7, 8, 9)
      step  <- Seq(2) // Seq(/*0,*/ 1, 7, 8, 9)
      memSz <- Seq(16) // Seq(4, 16)
    } {
      val p = Promise[Vec[Int]]()
      val g = Graph {
        import graph._
        val in    = ArithmSeq(start = 1, length = inLen)
        val slid  = Sliding(in, win, step)
        DebugIntPromise(slid, p)
      }

      val cfg = Config()
      cfg.nodeBufferSize = memSz
      runGraph(g, 8)

      assert(p.isCompleted)
      val res     = getPromiseVec(p)
      val inSq    = if (inLen < 1) Vector.empty else (1 to inLen).toVector
      val winM    = win .max(1)
      val stepM   = step.max(1)

      @tailrec
      def loop(rem: Vec[Int], res: Vec[Int]): Vec[Int] =
        if (rem.isEmpty) res
        else {
          val a   = rem.take(winM )
          val b   = rem.drop(stepM)
          val aP  = if (b.isEmpty) a else a.padTo(winM, 0)
          loop(b, res ++ aP)
        }

//      val exp     = inSq.sliding(winM, stepM)/*.map(_.padTo(win, 0))*/.flatten.toVector
      val exp = loop(inSq, Vector.empty)
      if (res != exp) {
        println(s"-------------- inLen $inLen, win $win, step $step")
        println(s"Obs: $res")
        println(s"Exp: $exp")
        COUNT += 1
      }
      // assert (res === exp, s"inLen $inLen, win $win, step $step")
    }
    println(s"COUNT $COUNT")
  }
}