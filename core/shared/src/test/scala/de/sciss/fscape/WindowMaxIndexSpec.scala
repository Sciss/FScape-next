package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.annotation.tailrec
import scala.concurrent.Promise
import scala.util.Success

class WindowMaxIndexSpec extends UGenSpec {
  def mkExpected(in: Vec[Double]): Int = {
    if (in.isEmpty) 0 else {
      val mx = in.max
      in.indexOf(mx)
    }
  }

  "The WindowMaxIndex UGen" should "work as intended" in {
    val p   = Promise[Vec[Int]]()

    val inLen       = 385
    val winSzSq     = List(56, 36, 59, 26, 18, 49, 55, 41, 45)
    val rnd         = new util.Random(2L)
    val inData      = Vector.fill(inLen)(rnd.nextDouble() * 2 - 1)
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
    val expected: Vector[Int] = inDataSq.iterator.map { w =>
      mkExpected(w)
    }.toVector

    val g = Graph {
      import graph._
      val in        = ValueDoubleSeq(inData: _*)
      val winSz     = ValueIntSeq(winSzSq : _*)
      val out       = WindowMaxIndex(in, winSz)
      DebugIntPromise(out, p)
    }

    runGraph(g, 128)

    assert(p.isCompleted)
    val res = p.future.value.get
    assert (res === Success(expected))
  }
}