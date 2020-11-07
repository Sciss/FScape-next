package de.sciss.fscape

import de.sciss.fscape.Ops._
import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class Issue59 extends UGenSpec {
  "Multiplication by zero" should "not be replaced by constant" in {
    val p = Promise[Vec[Double]]()
    val n = 123
    val g = Graph {
      import graph._
      val in = WhiteNoise(0.0).take(n)
      // Frames(in).poll(44100, "frames")
      DebugDoublePromise(in, p)
    }

    runGraph(g)

    assert(p.isCompleted)
    val res = getPromiseVec(p)
    val exp = Vector.fill(n)(0.0)
    assert (res === exp)
  }
}