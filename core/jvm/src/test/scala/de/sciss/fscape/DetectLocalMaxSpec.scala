package de.sciss.fscape

import de.sciss.fscape.Ops._
import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class DetectLocalMaxSpec extends UGenSpec {
  "The DetectLocalMax UGen" should "work as intended" in {
    val p = Promise[Vec[Int]]()
    val g = Graph {
      import graph._
      val sz      = 1024
      val period  = 100
      val block   = 2 * period + 1
      val gen     = SinOsc(1.0/period).take(sz) * Line(0.5, 1.0, sz)
      val det     = DetectLocalMax(gen, size = block)
      val d       = Frames(det).filter(det)
      DebugIntPromise(d, p)
    }

    runGraph(g, 1024)

    assert(p.isCompleted)
    val res = getPromiseVec(p)
    assert (res === Vec(226, 726))
  }
}