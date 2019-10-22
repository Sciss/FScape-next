package de.sciss.fscape

import de.sciss.kollflitz.Vec
import org.scalatest.{FlatSpec, Matchers}

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Success

class ResizeWindowSpec extends FlatSpec with Matchers {
  "The ResizeWindow UGen" should "work as intended" in {
    def variant(inLen: Int, winInSz: Int, start: Int, stop: Int): Unit = {
      val p = Promise[Vec[Int]]()

      val inData      = 1 to inLen
      val inDataP     = if (inData.nonEmpty) inData else Vector(0)
      val inDataSq    = inDataP.grouped(winInSz)
      val expected: Vector[Int] = inDataSq.flatMap { in0 =>
        val in1 = if (in0.size >= winInSz) in0 else in0.padTo(winInSz, 0)
        val in2 = if (start < 0) Vector.fill(-start)(0) ++ in1  else in1.drop     (start)
        val in3 = if (stop  > 0) in2 ++ Vector.fill(stop)(0)    else in2.dropRight(-stop)
        in3
      } .toVector

      val g = Graph {
        import graph._
        val oneTwo  = ArithmSeq(start = 1, length = inLen)
        val r       = ResizeWindow(oneTwo, size = winInSz, start = start, stop = stop)
        DebugIntPromise(r, p)
      }

      val cfg = stream.Control.Config()
      cfg.blockSize = 128
      val ctl = stream.Control(cfg)
      ctl.run(g)
      Await.result(ctl.status, Duration.Inf)

      assert(p.isCompleted)
      val res = p.future.value.get
      assert (res === Success(expected), s"for inLen = $inLen, winInSz = $winInSz, start = $start, stop = $stop")
    }

    for {
      inLen   <- List(0) // List(0, 1, 2, 10, 200)
      winInSz <- List(1) // List(1, 2, 9)
      start   <- List(-3) // -3 to +3
      stop    <- List(-2) // -3 to +3
    } {
      variant(inLen = inLen, winInSz = winInSz, start = start, stop = stop)
    }
  }

  it should "work with varying window parameters" in {
    val p   = Promise[Vec[Int]]()

    val inLen       = 385
    val winSzInSq   = List(56, 36, 59, 26, 18, 49, 55, 41, 45)
    assert (winSzInSq.sum == inLen)
    val inData      = List.tabulate(inLen)(i => i)
    val maxWinSz    = 56
    val winSzOutSq  = winSzInSq.map(_ min maxWinSz)
    val inDataSq    = {
      @tailrec
      def loop(rem: List[Int], sz: List[Int], res: List[List[Int]]): List[List[Int]] =
        sz match {
          case head :: tail =>
            val (remHd, remTl) = rem.splitAt(head)
            loop(remTl, tail, remHd :: res)

          case Nil => res.reverse
        }

      loop(inData, winSzInSq, Nil)
    }
    val expected: Vector[Int] = (inDataSq zip winSzOutSq).iterator.flatMap { case (w, sz) => w take sz }.toVector

    val g = Graph {
      import graph._
      val in          = ArithmSeq().take(inLen)
      val winSzIn: GE = winSzInSq.map(i => i: GE).reduce(_ ++ _)
      val winSzOut = winSzIn.min(maxWinSz)
      val dStop = winSzOut - winSzIn
      // dStop.poll(1, "dStop") // (0, 0, -3, 0, 0, 0, 0, 0, 0) -- correct
      // winSzIn.poll(1, "winSzIn")
      val out = ResizeWindow(in, winSzIn, stop = dStop)

      // Length(in ).poll("in .length") // 385 -- correct
      Length(out) // .poll("out.length") // 392 -- wrong
      DebugIntPromise(out, p)
    }

    val cfg = stream.Control.Config()
    cfg.blockSize = 128
    val ctl = stream.Control(cfg)
    ctl.run(g)
    Await.result(ctl.status, Duration.Inf)

    assert(p.isCompleted)
    val res = p.future.value.get
    assert (res === Success(expected))
  }
}