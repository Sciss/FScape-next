package de.sciss.fscape

import de.sciss.kollflitz.Vec
import de.sciss.numbers.IntFunctions
import org.scalatest.{FlatSpec, Matchers}

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Success

class RotateWindowSpec extends FlatSpec with Matchers {
  def mkExpected(in: Vec[Int], amount: Int): Vec[Int] = {
    // e.g. winSize = 4, amt = 1, expected = [DABC]
    // -> amtM = 1, amtI = 3
    // -> slice(3, 4) ++ slice(0, 3)
    // e.g. winSize = 4, amt = -1, expected = [BCDA]
    // -> amtM = 3, amtI = 1
    // -> slice(1, 4) ++ slice(0, 1)\
    val winSz = in.size
    val amtM  = IntFunctions.mod(amount, winSz)
    val amtI  = winSz - amtM
    in.slice(amtI, winSz) ++ in.slice(0, amtI)
  }

  "The RotateWindow UGen" should "work as intended" in {
    def variant(inLen: Int, winSz: Int, amount: Int): Unit = {
      val p = Promise[Vec[Int]]()

      val inData      = 1 to inLen
      val inDataP     = if (inData.nonEmpty) inData else Vector(1)
      val inDataSq    = inDataP.grouped(winSz)
      val expected: Vector[Int] = inDataSq.flatMap { in0 =>
        val in1 = if (in0.size >= winSz) in0 else in0.padTo(winSz, 0)
        val in4 = mkExpected(in1, amount)
        assert (in4.size == winSz)
        in4
      } .toVector

      val g = Graph {
        import graph._
        val in  = ArithmSeq(start = 1, length = inLen)
        val r   = RotateWindow(in, size = winSz, amount = amount)
        DebugIntPromise(r, p)
      }

      val cfg = stream.Control.Config()
      cfg.blockSize = 128
      val ctl = stream.Control(cfg)
      ctl.run(g)
      val info = s"for inLen = $inLen, winInSz = $winSz, start = $amount"
      Await.result(ctl.status, Duration.Inf)

      assert(p.isCompleted)
      val res = p.future.value.get
      assert (res === Success(expected), info)
    }

    for {
      inLen   <- List(0, 1, 2, 10, 200)
      winSz   <- List(1, 2, 9)
      amount  <- -3 to +3
    } {
      variant(inLen = inLen, winSz = winSz, amount = amount)
    }
  }

  it should "work with varying window parameters" in {
    val p   = Promise[Vec[Int]]()

    val inLen       = 385
    val winSzSq     = List(56, 36, 59, 26, 18, 49, 55, 41, 45)
    val rnd         = new util.Random(0L)
    val amountSq    = Vector.fill(4)(rnd.nextInt(40) - 20)
    assert (winSzSq.sum == inLen)
    val inData      = Vector.tabulate(inLen)(i => i)
    val inDataSq    = {
      @tailrec
      def loop(rem: Vector[Int], sz: List[Int], res: Vector[Vector[Int]]): Vector[Vector[Int]] =
        sz match {
          case head :: tail =>
            val (remHd, remTl) = rem.splitAt(head)
            loop(remTl, tail, res :+ remHd)

          case Nil => res
        }

      loop(inData, winSzSq, Vector.empty)
    }
    val amountSqP = amountSq.padTo(inDataSq.size, amountSq.last)
    val expected: Vector[Int] = (inDataSq zip amountSqP).iterator.flatMap {
      case (w, amt) => mkExpected(w, amt)
    }.toVector

    val g = Graph {
      import graph._
      val in        = ArithmSeq().take(inLen)
      val winSz     = ValueIntSeq(winSzSq : _*)
      val amount    = ValueIntSeq(amountSq: _*)
      val out       = RotateWindow(in, winSz, amount = amount)
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