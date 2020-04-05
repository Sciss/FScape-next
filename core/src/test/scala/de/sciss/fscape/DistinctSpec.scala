package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.concurrent.Promise
import scala.util.Success

class DistinctSpec extends UGenSpec {
  "The Distinct UGen" should "work as intended" in {
    val p = Promise[Vec[Int]]()
    val g = Graph {
      import graph._
      val in  = ArithmSeq(start = 1, length = 20) % 4
      val d   = Distinct(in.take(200))
      DebugIntPromise(d, p)
    }

    runGraph(g)

    assert(p.isCompleted)
    val res = p.future.value.get
    assert (res === Success(Seq(1, 2, 3, 0)))
  }
}