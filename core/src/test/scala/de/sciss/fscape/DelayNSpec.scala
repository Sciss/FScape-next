package de.sciss.fscape

import de.sciss.fscape.stream.Control.Config
import de.sciss.kollflitz.Vec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Success

class DelayNSpec extends AnyFlatSpec with Matchers {
  "The DelayNSpec UGen" should "work as intended" in {
    for {
      padLen  <- Seq(10)   // Seq(0, 1, 10, 100, 512)
      dlyLen  <- Seq(-1)  // Seq(-1, 0, 1, 10, 100, 512, 513, 2000)
      maxLen  <- Seq(1)   // Seq(-1, 0, 1, 10, 100, 512, 513, 2000)
    } {
      val p = Promise[Vec[Int]]()
      val g = Graph {
        import graph._
        val in    = ArithmSeq(start = 1, length = 4) ++ DC(0).take(padLen)
        val tw    = DelayN(in, maxLength = maxLen, length = dlyLen)
        DebugIntPromise(tw, p)
      }

      val cfg = Config()
      cfg.blockSize = 512
      val ctl = stream.Control(cfg)
      ctl.run(g)
      Await.result(ctl.status, Duration.Inf)

      assert(p.isCompleted)
      val res         = p.future.value.get
      val inSq        = (1 to 4) ++ Vector.fill(padLen)(0)
      val dlyLenClip  = math.max(0, math.min(dlyLen, maxLen))
      val postLen     = maxLen - dlyLenClip
      val exp         = Vector.fill(dlyLenClip)(0) ++ inSq // ++ Vector.fill(postLen)(0)
      assert (res === Success(exp), s"padLen $padLen, dlyLen $dlyLen, maxLen $maxLen")
    }
  }
}