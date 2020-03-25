package de.sciss.fscape

import de.sciss.kollflitz.Vec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Success

class DropWhileSpec extends AnyFlatSpec with Matchers {
  "The DropWhile UGen" should "work as intended" in {
    for {
      inLen   <- Seq(0, 1, 10, 100, 1024, 1025)
      pLen    <- Seq(0, 1, 10, 100, 1024, 1025, 2000)
      pThresh <- Seq(0, 1, 10, 100, 1024, 1025, 2000)
    } {
      val p = Promise[Vec[Int]]()
      val g = Graph {
        import graph._
        val in    = ArithmSeq(start = 1, length = inLen)
        val pred  = ArithmSeq(start = 1, length = pLen ) >= pThresh
        val dw    = in.dropWhile(pred)
        //        in  .poll(1, "in")
        //        pred.poll(1, "pr")
        //        dw  .poll(1, "dw")
        DebugIntPromise(dw, p)
      }

      val ctl = stream.Control()
      ctl.run(g)
      Await.result(ctl.status, Duration.Inf)

      assert(p.isCompleted)
      val res     = p.future.value.get
      val inSq    = if (inLen < 1) Vector.empty else (1 to inLen).toVector
      val predSq  = if (pLen  < 1) Vector.empty else (1 to pLen).map(_ >= pThresh)
      val predSqP = if (predSq.isEmpty) predSq else predSq.padTo(inSq.size, predSq.last)
      val exp     = if (predSqP.isEmpty) Vector.empty else {
        inSq.zipWithIndex.dropWhile { case (_, idx) => predSqP(idx) } .map(_._1)
      }
      assert (res === Success(exp), s"inLen $inLen, pLen $pLen, pThresh $pThresh")
    }
  }
}