package de.sciss.fscape

import de.sciss.kollflitz
import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class DifferentiateSpec extends UGenSpec {
  "The Differentiate UGen" should "work as intended" in {
    for {
      len <- Seq(0, 1, 10, 63, 64, 65)
    } {
      val p     = Promise[Vec[Double]]()
      val r     = new util.Random(2L)
      val inSq  = Vector.fill(len)(r.nextDouble())
      val g = Graph {
        import graph._
        val in  = ValueDoubleSeq(inSq: _*)
        val d   = in.differentiate
        DebugDoublePromise(d, p)
      }

      runGraph(g, 64)

      import kollflitz.Ops._
      val res = asD(getPromiseVec(p))
      val exp = if (inSq.isEmpty) Vector.empty else inSq.head +: inSq.differentiate
      difOk(res, exp, s"len $len")
    }
  }
}