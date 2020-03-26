package de.sciss.fscape

import de.sciss.fscape.stream.Control.Config
import de.sciss.kollflitz.Vec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Success

class DistinctSpec extends AnyFlatSpec with Matchers {
  "The Distinct UGen" should "work as intended" in {
    val p = Promise[Vec[Int]]()
    val g = Graph {
      import graph._
      val in  = ArithmSeq(start = 1, length = 20) % 4
      val d   = Distinct(in.take(200))
      DebugIntPromise(d, p)
    }

    val cfg = Config()
    cfg.blockSize = 1024
    val ctl = stream.Control(cfg)
    ctl.run(g)
    Await.result(ctl.status, Duration.Inf)

    assert(p.isCompleted)
    val res = p.future.value.get
    assert (res === Success(Seq(1, 2, 3, 0)))
  }
}