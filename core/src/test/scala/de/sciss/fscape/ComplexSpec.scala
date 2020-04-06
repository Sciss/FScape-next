package de.sciss.fscape

import de.sciss.fscape.graph.ValueDoubleSeq
import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class ComplexSpec extends UGenSpec {
  override val eps: Double = 1.0e-2

  def toFlat(in: Vec[C]): Vec[Double] = in.flatMap(c => List(c.re, c.im))
  def toGE  (in: Vec[C]): GE          = ValueDoubleSeq(toFlat(in): _*)

  case class C(re: Double, im: Double) {
    def * (that: C): C = {
      val outRe = this.re * that.re - this.im * that.im
      val outIm = this.re * that.im + this.im * that.re
      C(outRe, outIm)
    }
  }

  def run(exp: Vec[Double], name: String, mce: Boolean = false)(thunk: => GE): Unit = {
    val p   = Promise[Vec[Double]]()
    val g = Graph {
      import graph._
      val in  = thunk
      val sig = if (mce) in.out(0) ++ in.out(1) else in
      DebugDoublePromise(sig, p)
    }

    runGraph(g, 128)

    assert(p.isCompleted)
    val res = p.future.value.get
    assert (res.isSuccess)
    val obs = res.get

    difOk(obs, exp, s"$name ; mce = $mce")
  }

  "The .complex unary math operations" should "work as intended" in {
    // .complex.abs
    run(Vec(1.0,0.0,  1.0,0.0,  0.0,0.0,  1.41,0.0), "abs") {
      val in = Vec(C(1, 0), C(0, 1), C(0, 0), C(1, 1))
      toGE(in).complex.abs
    }

    // .complex.mag
    run(Vec(1.0,  1.0,  0.0,  1.41), "mag") {
      val in = Vec(C(1, 0), C(0, 1), C(0, 0), C(1, 1))
      toGE(in).complex.mag
    }

    // .complex.abs with MCE
    run(Vec(1.0,0.0,  1.0,0.0,  0.0,0.0,  1.41,0.0), "abs", mce = true) {
      val in1 = Vec(C(1, 0), C(0, 1))
      val in2 = Vec(C(0, 0), C(1, 1))
      val in: GE = Seq(toGE(in1), toGE(in2))
      in.complex.abs
    }

    // .complex.mag with MCE
    run(Vec(1.0,  1.0,  0.0,  1.41), "mag", mce = true) {
      val in1 = Vec(C(1, 0), C(0, 1))
      val in2 = Vec(C(0, 0), C(1, 1))
      val in: GE = Seq(toGE(in1), toGE(in2))
      in.complex.mag
    }

    // .complex.real
    run(Vec(1.0,  0.0,  0.0,  1.0), "real") {
      val in = Vec(C(1, 0), C(0, 1), C(0, 0), C(1, 1))
      toGE(in).complex.real
    }

    // .complex.real with MCE
    run(Vec(1.0,  0.0,  0.0,  1.0), "real", mce = true) {
      val in1 = Vec(C(1, 0), C(0, 1))
      val in2 = Vec(C(0, 0), C(1, 1))
      val in: GE = Seq(toGE(in1), toGE(in2))
      in.complex.real
    }

    // .complex.imag
    run(Vec(0.0,  1.0,  0.0,  1.0), "imag") {
      val in = Vec(C(1, 0), C(0, 1), C(0, 0), C(1, 1))
      toGE(in).complex.imag
    }

    // .complex.imag with MCE
    run(Vec(0.0,  1.0,  0.0,  1.0), "imag", mce = true) {
      val in1 = Vec(C(1, 0), C(0, 1))
      val in2 = Vec(C(0, 0), C(1, 1))
      val in: GE = Seq(toGE(in1), toGE(in2))
      in.complex.imag
    }

    // .complex.phase
    run(Vec(0.0,  Pi/2,  -Pi/2,  Pi/4,  -Pi/4, -Pi*3/4), "phase") {
      val in = Vec(C(1, 0), C(0, 1), C(0, -1), C(1, 1), C(1, -1), C(-1, -1))
      toGE(in).complex.phase
    }

    // .complex.phase with MCE
    run(Vec(0.0,  Pi/2,  -Pi/2,  Pi/4,  -Pi/4, -Pi*3/4), "phase", mce = true) {
      val in1 = Vec(C(1, 0), C(0, 1), C(0, -1))
      val in2 = Vec(C(1, 1), C(1, -1), C(-1, -1))
      val in: GE = Seq(toGE(in1), toGE(in2))
      in.complex.phase
    }
  }

  "The .complex binary math operations" should "work as intended" in {
    // .complex.times
    val inA = Vec(C(1.0, 0.0), C(0.0, 1.0), C(0.0, 0.0), C(1.0, 1.0), C(1.0, 0.0), C(0.0, 1.0), C(0.0, 0.0), C(1.0, 1.0))
    val inB = Vec(C(0.4, 0.2), C(0.0, 0.5), C(0.6, 0.0), C(0.4, 0.2), C(0.0, 0.5), C(0.6, 0.0), C(0.4, 0.2), C(0.0, 0.5))
    val exp = (inA zip inB).map { case (a, b) => a * b }

    run(toFlat(exp), "times") {
      toGE(inA).complex * toGE(inB)
    }

    val inALong = (0 to 100).flatMap(_ => inA)
    val inBLong = (0 to 100).flatMap(_ => inB)
    val expLong = (inALong zip inBLong).map { case (a, b) => a * b }

    run(toFlat(expLong), "times (long)") {
      toGE(inALong).complex * toGE(inBLong)
    }
  }
}