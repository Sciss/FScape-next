package de.sciss.fscape

import de.sciss.kollflitz.Vec
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Success

class OverlapAddSpec extends FlatSpec with Matchers {
  "The OverlapAdd UGen" should "run for the expected time (issue 27)" in {
    val n           = 1000
    val win         = 100
    val step        = 10
    val numWin      = (n + step - 1) / step
    val penUlt      = numWin - 2
    val penUltOff   = penUlt * step
    val penUltStop  = penUltOff + win
    val ultLen      = n - ((numWin - 1) * step)
    val ultOff      = penUltOff + step
    val ultStop     = ultOff + ultLen
    val expected    = math.max(penUltStop, ultStop)
    val p           = Promise[Vec[Int]]()

    val g = Graph {
      import graph._
      val in  = DC(0).take(n)
      val sig = OverlapAdd(in, size = win, step = step)
      DebugIntPromise(Length(sig), p)
    }

    val ctl = stream.Control()
    ctl.run(g)
    Await.result(ctl.status, Duration.Inf)

    assert(p.isCompleted)
    val res = p.future.value.get
    assert (res === Success(Vec(expected)))
  }
}