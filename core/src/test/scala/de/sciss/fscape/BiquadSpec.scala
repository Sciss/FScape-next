package de.sciss.fscape

import de.sciss.kollflitz.Vec
import de.sciss.numbers

import scala.concurrent.Promise

class BiquadSpec extends UGenSpec {
  "The Biquad UGen" should "work as specified" in {
    for {
      len <- List(0, 1, 10, 127, 128, 129)
      b0  <- List(0.0, 0.9, -1.0)
      b1  <- List(0.0, 0.7, -0.8)
      b2  <- List(0.0, 0.5, -0.6)
      a1  <- List(0.0, 0.3, -0.4)
      a2  <- List(0.0, 0.1, -0.2)
    } {
      val p = Promise[Vec[Double]]()

      val g = Graph {
        import graph._

        val in  = Line(0.0, 1.0, len)
//        in.poll(1, "in")
        val v   = Biquad(in, b0 = b0, b1 = b1, b2 = b2, a1 = a1, a2 = a2)
        DebugDoublePromise(v, p)
      }

      val info = s"For len $len, b0 $b0, b1 $b1, b2 $b2, a1 $a1, a2 $a2"
//      println(info)
      runGraph(g, 128)
//      val cfg = Config()
//      cfg.blockSize = 256
//      val ctl = stream.Control(cfg)
//      ctl.run(g)
//      Swing.onEDT {
//        SimpleGUI(ctl)
//      }
//      Await.result(ctl.status, Duration.Inf)

      assert(p.isCompleted)
      val obs = getPromiseVec(p)

      import numbers.Implicits._
      val inSq = if (len == 1) Vector(0.0) else Vector.tabulate(len)(i => i.linLin(0, len - 1, 0.0, 1.0))

      val expB = Vector.newBuilder[Double]
      var _x1 = 0.0
      var _x2 = 0.0
      var _y1 = 0.0
      var _y2 = 0.0

      var i = 0
      while (i < len) {
        val x0 = inSq(i)
        val y0 = b0 * x0 + b1 * _x1 + b2 * _x2 - a1 * _y1 - a2 * _y2
        expB += y0
        _y2 = _y1
        _y1 = y0
        _x2 = _x1
        _x1 = x0
        i += 1
      }

      val exp = expB.result()
      difOk(obs, exp, info)
    }
  }
}