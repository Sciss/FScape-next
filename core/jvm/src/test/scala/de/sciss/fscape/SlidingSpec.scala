package de.sciss.fscape

import de.sciss.fscape.stream.Control.Config
import de.sciss.kollflitz.Vec

import scala.annotation.tailrec
import scala.concurrent.Promise

class SlidingSpec extends UGenSpec {
  "The Sliding UGen" should "work as intended" in {
//    var COUNT = 0
    for {
      inLen <- Seq(/*0,*/ 1, 7, 8, 9)
      win   <- Seq(/*0,*/ 1, 7, 8, 9)
      step  <- Seq(/*0,*/ 1, 7, 8, 9)
      memSz <- Seq(4, 16)
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
      runGraph(g, 8, cfg)

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
//      if (res != exp) {
//        println(s"-------------- inLen $inLen, win $win, step $step")
//        println(s"Obs: $res")
//        println(s"Exp: $exp")
//        COUNT += 1
//      }
       assert (res === exp, s"inLen $inLen, win $win, step $step")
    }
//    println(s"COUNT $COUNT")
  }

  it should "adhere to the documentation example" in {
    val p = Promise[Vec[Int]]()
    val g = Graph {
      import graph._
      val slid = Sliding(ArithmSeq(1, length = 4), size = 3, step = 1)
      DebugIntPromise(slid, p)
    }

    runGraph(g)

    assert(p.isCompleted)
    val res = getPromiseVec(p)
    val exp = Vec(1, 2, 3, 2, 3, 4, 3, 4, 0, 4)
    assert(res === exp)
  }
}