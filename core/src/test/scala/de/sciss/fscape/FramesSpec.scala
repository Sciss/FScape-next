package de.sciss.fscape

import de.sciss.kollflitz.Vec
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.{Success, Try}

class FramesSpec extends FlatSpec with Matchers {
  "The Frames UGen" should "work as specified" in {
    val lengths = List(
      0, 1, 10, 100, 1000, 10000
    )

    lengths.foreach { n =>
      val p = Promise[Vec[Int]]()

      val g = Graph {
        import graph._

        val x = DC(1).take(n)
        val v = Frames(x)
//        v.poll(0, "bla")
        DebugIntPromise(v, p)
      }

      val cfg = stream.Control.Config()
      cfg.blockSize = 512
      val ctl = stream.Control()
//      showStreamLog = true
      ctl.run(g)
      Await.result(ctl.status, Duration.Inf)

      assert(p.isCompleted)
      val res: Try[Vec[Int]] = p.future.value.get
      val exp = if (n == 0) Nil else 1 to n
      assert (res === Success(exp), s"$res does not match $n")
    }
  }
}