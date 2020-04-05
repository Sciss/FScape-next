package de.sciss.fscape

import de.sciss.fscape.graph.NormalizeWindow._
import de.sciss.kollflitz.Vec

import scala.annotation.{switch, tailrec}
import scala.concurrent.Promise
import scala.util.Success

class NormalizeWindowSpec extends UGenSpec {
  def mkExpected(win: Vector[Double], mode: Int): Vector[Double] = {
    val (add, mul, add2) = (mode: @switch) match {
      case Normalize    => val gain = win.map(math.abs).max; (0.0, 1.0 / gain, 0.0)
      case FitUnipolar  => val min = win.min; val max = win.max; (-min, 1.0 / (max - min), 0.0)
      case FitBipolar   => val min = win.min; val max = win.max; (-min, 2.0 / (max - min), -1.0)
      case ZeroMean     => val mean = win.sum / win.size; (-mean, 1.0, 0.0)
    }
    win.map(x => (x + add) * mul + add2)
  }

  "The NormalizeWindow UGen" should "work as intended" in {

    def variant(inLen: Int, winSz: Int, mode: Int): Unit = {
      val p = Promise[Vec[Double]]()

      val rnd         = new util.Random(0L)
      val inData      = Vector.fill(inLen)(rnd.nextDouble())
      val inDataSq    = inData.grouped(winSz)
      val expected: Vector[Double] = inDataSq.flatMap { in0 =>
        val in1 = if (in0.size >= winSz) in0 else in0.padTo(winSz, 0.0)
        val in4 = mkExpected(in1, mode = mode)
        in4
      } .toVector

      val g = Graph {
        import graph._
        val in  = ValueDoubleSeq(inData: _*)
        val r   = NormalizeWindow(in, size = winSz, mode = mode)
        DebugDoublePromise(r, p)
      }

      runGraph(g, 128)
      val info = s"for inLen = $inLen, winInSz = $winSz, mode = $mode"

      assert(p.isCompleted)
      val res = p.future.value.get
      assert (res === Success(expected), info)
    }

    for {
      inLen   <- List(0, 1, 2, 10, 200)
      winSz   <- List(/*1,*/ 2, 9)  // UGen avoid NaNs; still we should not encourage a spec on win-size 1
      mode    <- 0 to ModeMax
    } {
      variant(inLen = inLen, winSz = winSz, mode = mode)
    }
  }

  it should "work with varying window parameters" in {
    val p   = Promise[Vec[Double]]()

    val winSzSq     = List(3, 4, 5)
    val inLen       = winSzSq.sum
    val modeSq      = List(0, 1)
    val modeSqP     = modeSq.padTo(winSzSq.size, modeSq.last)
    val rnd         = new util.Random(1L)
    val inData      = Vector.fill(inLen)(rnd.nextDouble())
    val inDataSq    = {
      @tailrec
      def loop(rem: Vector[Double], sz: List[Int], res: Vector[Vector[Double]]): Vector[Vector[Double]] =
        sz match {
          case head :: tail =>
            val (remHd, remTl) = rem.splitAt(head)
            loop(remTl, tail, res :+ remHd)

          case Nil => res
        }

      loop(inData, winSzSq, Vector.empty)
    }
    val expected: Vector[Double] = (inDataSq zip modeSqP).iterator.flatMap {
      case (w, mode) => mkExpected(w, mode)
    }.toVector

    val g = Graph {
      import graph._
      val winSz = ValueIntSeq(winSzSq : _*)
      val mode  = ValueIntSeq(modeSq  : _*)
      val in    = ValueDoubleSeq(inData: _*)
      val r     = NormalizeWindow(in, size = winSz, mode = mode)
      DebugDoublePromise(r, p)
    }

    runGraph(g, 128)

    assert(p.isCompleted)
    val res = p.future.value.get
    assert (res === Success(expected))
  }
}