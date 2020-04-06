package de.sciss.fscape

import de.sciss.kollflitz.Vec
import de.sciss.numbers

import scala.concurrent.Promise
import scala.math.{cos, pow}

class DEnvGenSpec extends UGenSpec {
  "The DEnvGen UGen" should "work as intended" in {
    val p = Promise[Vec[Double]]()
    val levels    = Seq(0.0, -1.0, 1.0, 0.1)
    val lengths   = Seq(   100,  200, 50)
    val shapes    = Seq(   1,    3,   2)    // lin, sine, exp
    val g = Graph {
      import graph._

      val env = DEnvGen(
        levels  = ValueDoubleSeq(levels : _*),
        lengths = ValueIntSeq   (lengths: _*),
        shapes  = ValueIntSeq   (shapes : _*)
      )

      DebugDoublePromise(env, p)
    }

    runGraph(g, 128)

    assert(p.isCompleted)
    val res = getPromiseVec(p)

    val linLevelAt: (Double, Double, Double) => Double = { (pos, y1, y2) =>
      pos * (y2 - y1) + y1
    }

    val expLevelAt: (Double, Double, Double) => Double = { (pos, y1, y2) =>
      if (y1 == 0) {
        if (pos >= 0.5) y2 else y1
      } else {
        y1 * pow(y2 / y1, pos)
      }
    }

    val sinLevelAt: (Double, Double, Double) => Double = { (pos, y1, y2) =>
      y1 + (y2 - y1) * (-cos(Pi * pos) * 0.5 + 0.5)
    }

    val exp = (levels.sliding(2).toVector zip lengths zip shapes).flatMap {
      case ((Seq(start, end), len), shape) =>
        val fun = shape match {
          case 1 => linLevelAt
          case 2 => expLevelAt
          case 3 => sinLevelAt
        }
        import numbers.Implicits._
        Vector.tabulate(len)(i => fun(i.linLin(0, len, 0.0, 1.0), start, end))
    }

    difOk(res, exp)
  }
}