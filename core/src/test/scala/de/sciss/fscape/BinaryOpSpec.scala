package de.sciss.fscape

import de.sciss.kollflitz.Vec
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.language.implicitConversions
import scala.util.Success

class BinaryOpSpec extends FlatSpec with Matchers {
  "The binary-op ugen" should "work as intended" in {
    val p1    = Promise[Vec[Int]]()
    val p2    = Promise[Vec[Int]]()
    val sz    = 1024
    val cfg   = stream.Control.Config()
    cfg.blockSize = sz
    val exp1  = Vector(sz + 1)
    val exp2  = Vector((sz + 1) * 2)

    // this was a bug in BinaryOp correctly shutting down
    val g = Graph {
      import graph._
      val pre     = Line(0.0, 0.0, sz)
      val A       = pre.take(sz)
      val B       = Line(0.0, 0.0, sz + 1)
      val C       = A * B
      DebugIntPromise(Length(C), p1)
      val D       = C :+ 0.0
      val E       = pre ++ D
      DebugIntPromise(Length(E), p2)
    }

    val ctl = stream.Control(cfg)
    ctl.run(g)
    Await.result(ctl.status, Duration.Inf)

    assert(p1.isCompleted)
    assert(p2.isCompleted)
    val res1 = p1.future.value.get
    val res2 = p2.future.value.get
    assert (res1 === Success(exp1))
    assert (res2 === Success(exp2))
  }
}