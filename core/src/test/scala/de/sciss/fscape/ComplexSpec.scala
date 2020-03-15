package de.sciss.fscape

import de.sciss.kollflitz.Vec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}

class ComplexSpec extends AnyFlatSpec with Matchers {
  "The .complex math operations" should "work as intended" in {
    def run(expected: Vec[Double], mce: Boolean = false)(thunk: => GE): Unit = {
      val p   = Promise[Vec[Double]]()
      val g = Graph {
        import graph._
        val in  = thunk
        val sig = if (mce) in.out(0) ++ in.out(1) else in
        DebugDoublePromise(sig, p)
      }

      val cfg = stream.Control.Config()
      cfg.blockSize = 128
      val ctl = stream.Control(cfg)
      ctl.run(g)
      Await.result(ctl.status, Duration.Inf)

      assert(p.isCompleted)
      val res = p.future.value.get
      assert (res.isSuccess)
      val values = res.get

      values.size should be (expected.size)

      (values zip expected).foreach { case (v, e) =>
        v should be (e +- 0.05)
      }

//      assert (values === expected)
    }

    def toGE(in: Vec[C]): GE = in.flatMap(c => List[GE](c.re, c.im)).reduce(_ ++ _)

    case class C(re: Double, im: Double)

    // .complex.abs
    run(Vec(1.0,0.0,  1.0,0.0,  0.0,0.0,  1.41,0.0)) {
      val in = Vec(C(1, 0), C(0, 1), C(0, 0), C(1, 1))
      toGE(in).complex.abs
    }

    // .complex.mag
    run(Vec(1.0,  1.0,  0.0,  1.41)) {
      val in = Vec(C(1, 0), C(0, 1), C(0, 0), C(1, 1))
      toGE(in).complex.mag
    }

    // .complex.abs with MCE
    run(Vec(1.0,0.0,  1.0,0.0,  0.0,0.0,  1.41,0.0), mce = true) {
      val in1 = Vec(C(1, 0), C(0, 1))
      val in2 = Vec(C(0, 0), C(1, 1))
      val in: GE = Seq(toGE(in1), toGE(in2))
      in.complex.abs
    }

    // .complex.mag with MCE
    run(Vec(1.0,  1.0,  0.0,  1.41), mce = true) {
      val in1 = Vec(C(1, 0), C(0, 1))
      val in2 = Vec(C(0, 0), C(1, 1))
      val in: GE = Seq(toGE(in1), toGE(in2))
      in.complex.mag
    }

    // .complex.real
    run(Vec(1.0,  0.0,  0.0,  1.0)) {
      val in = Vec(C(1, 0), C(0, 1), C(0, 0), C(1, 1))
      toGE(in).complex.real
    }

    // .complex.real with MCE
    run(Vec(1.0,  0.0,  0.0,  1.0), mce = true) {
      val in1 = Vec(C(1, 0), C(0, 1))
      val in2 = Vec(C(0, 0), C(1, 1))
      val in: GE = Seq(toGE(in1), toGE(in2))
      in.complex.real
    }

    // .complex.imag
    run(Vec(0.0,  1.0,  0.0,  1.0)) {
      val in = Vec(C(1, 0), C(0, 1), C(0, 0), C(1, 1))
      toGE(in).complex.imag
    }

    // .complex.imag with MCE
    run(Vec(0.0,  1.0,  0.0,  1.0), mce = true) {
      val in1 = Vec(C(1, 0), C(0, 1))
      val in2 = Vec(C(0, 0), C(1, 1))
      val in: GE = Seq(toGE(in1), toGE(in2))
      in.complex.imag
    }

    import math.Pi

    // .complex.phase
    run(Vec(0.0,  Pi/2,  -Pi/2,  Pi/4,  -Pi/4, -Pi*3/4)) {
      val in = Vec(C(1, 0), C(0, 1), C(0, -1), C(1, 1), C(1, -1), C(-1, -1))
      toGE(in).complex.phase
    }

    // .complex.phase with MCE
    run(Vec(0.0,  Pi/2,  -Pi/2,  Pi/4,  -Pi/4, -Pi*3/4), mce = true) {
      val in1 = Vec(C(1, 0), C(0, 1), C(0, -1))
      val in2 = Vec(C(1, 1), C(1, -1), C(-1, -1))
      val in: GE = Seq(toGE(in1), toGE(in2))
      in.complex.phase
    }
  }
}