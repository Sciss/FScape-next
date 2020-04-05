package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.concurrent.Promise
import scala.util.Success

class Issue59 extends UGenSpec {
  "Multiplication by zero" should "not be replaced by constant" in {
    val p = Promise[Vec[Double]]()
    val n = 123
    val g = Graph {
      import graph._
      val in = WhiteNoise(0.0).take(n)
      DebugDoublePromise(in, p)
    }

    runGraph(g)

    assert(p.isCompleted)
    val res     = p.future.value.get
    val exp     = Vector.fill(n)(0.0)
    assert (res === Success(exp))
  }
}