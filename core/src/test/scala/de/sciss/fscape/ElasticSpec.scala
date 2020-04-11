package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class ElasticSpec extends UGenSpec {
  // XXX TODO --- we should also test for failure if `.elastic` is not present (time out and kill rendering?)
  "The Elastic UGen" should "work as intended" in {
    val n = 32
    val m = 16
    val k = 3
    val p = Promise[Vec[Int]]()
    val g = Graph {
      import graph._
      val in  = ArithmSeq(length = k * n) % m
      val red = ReduceWindow.max(in, k * n)
      val rep = RepeatWindow(red, 1, k * n)
      val dif = rep - in.elastic()  // would hang without `elastic`
      DebugIntPromise(dif, p)
    }

    runGraph(g, n)

    assert(p.isCompleted)
    val res   = getPromiseVec(p)
    val inSq  = Seq.tabulate(k * n)(i => i % m)
    val inRed = inSq.max
    val inRep = Seq.fill(k * n)(inRed)
    val exp   = (inRep zip inSq).map { case (a, b) => a - b }
    assert (res === exp)
  }
}