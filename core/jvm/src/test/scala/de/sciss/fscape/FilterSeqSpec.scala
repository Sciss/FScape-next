package de.sciss.fscape

import de.sciss.fscape.Ops._
import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class FilterSeqSpec extends UGenSpec {
  "The FilterSeq UGen" should "work as intended" in {
    for {
      len <- Seq(0, 1, 255, 256, 257)
    } {
      val thresh = 0.98
      val p = Promise[Vec[Double]]()
      val g = Graph {
        import graph._
        val gen  = SinOsc(1.0/100).take(len)
        val sh   = FilterSeq(gen, gen > thresh)
        DebugDoublePromise(sh, p)
      }

      runGraph(g, 256)

      assert(p.isCompleted)
      val res = getPromiseVec(p)
      val inSq = Vector.tabulate(len)(i => math.sin(i/100.0 * Pi2))
      val exp  = inSq.filter(_ > thresh)

      difOk(res, exp, s"len $len")
    }
  }
}