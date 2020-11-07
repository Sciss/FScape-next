package de.sciss.fscape

import de.sciss.fscape.Ops._
import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class DCT_II_Spec extends UGenSpec {
  override val eps: Double = 1.0e-2

  "The DCT_II UGen" should "work as intended" in {
    for {
      zero <- Seq(false, true)
    } {
      val numCoeffs = 16
      val p = Promise[Vec[Double]]()
      val g = Graph {
        import graph._
        val n = 1024
        val in = LFSaw(8.0/n).take(n)
        val out = DCT_II(in, n, numCoeffs, zero = if (zero) 1 else 0)
        DebugDoublePromise(out, p)
      }

      runGraph(g, 512)

      assert(p.isCompleted)
      val res = getPromiseVec(p)
      val exp0 = Vec(
        -8.0,
        -42.77,
        -2.23E-14,
        -43.68,
        -8.44E-15,
        -45.68,
        -1.40E-14,
        -49.23,
        -2.55E-15,
        -55.40,
        -1.56E-13,
        -67.04,
        -2.39E-13,
        -94.70,
        2.80E-14,
        -233.26,
        -3.71E-13,
      )
      val exp = if (zero) exp0.take(numCoeffs) else exp0.takeRight(numCoeffs)

      difOk(res, exp)
    }
  }
}