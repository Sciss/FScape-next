package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class GramSchmidtMatrixSpec extends UGenSpec {
  override val eps = 1.0e-2

  "The GramSchmidtMatrix UGen" should "work as intended" in {
    val p1 = Promise[Vec[Double]]()
    val p2 = Promise[Vec[Double]]()
    val g = Graph {
      import graph._
      val v1 = Vector(3.0, 1.0)
      val v2 = Vector(2.0, 2.0)
      val vs = ValueDoubleSeq(v1 ++ v2: _*)
      val us = GramSchmidtMatrix(vs, columns = 2, rows = 2, normalize = 0)
      val es = GramSchmidtMatrix(vs, columns = 2, rows = 2, normalize = 1)
//      RepeatWindow(us).poll(2, "us") // expected: (3, 1), (-0.4, 1.2)
//      RepeatWindow(es).poll(2, "es") // expected: (0.949, 0.316), (-0.316, 0.949)
      DebugDoublePromise(us, p1)
      DebugDoublePromise(es, p2)
    }

    runGraph(g)

    val res1 = getPromiseVec(p1)
    val res2 = getPromiseVec(p2)

    difOk(res1, Vec(3.0, 1.0, -0.4, 1.2))
    difOk(res2, Vec(0.949, 0.316, -0.316, 0.949))
  }
}