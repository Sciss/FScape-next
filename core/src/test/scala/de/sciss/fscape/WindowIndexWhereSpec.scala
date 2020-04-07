package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.annotation.tailrec
import scala.concurrent.Promise

class WindowIndexWhereSpec extends UGenSpec {
  def mkExpected(in: Vec[Int]): Int = in.indexWhere(_ > 0)

  "The WindowIndexWhere UGen" should "work as intended" in {
    val p   = Promise[Vec[Int]]()

    val rnd         = new util.Random(2L)
    val winSzSq     = List.fill(40)(rnd.nextInt(10))
    val inLen       = winSzSq.sum // 385
    val inData      = Vector.fill(inLen)(if (rnd.nextDouble() > 0.75) 1 else 0)
    // println(s"winSzSq $winSzSq")
    val inDataSq    = {
      @tailrec
      def loop[A](rem: Vector[A], sz: List[Int], res: Vector[Vector[A]]): Vector[Vector[A]] =
        sz match {
          case head :: tail =>
            val (remHd, remTl) = rem.splitAt(head)
            loop(remTl, tail, res :+ remHd)

          case Nil => res
        }

      loop(inData, winSzSq, Vector.empty)
    }
    // println(s"inDataSq $inDataSq")
    val expected: Vector[Int] = inDataSq.iterator.map { w =>
      mkExpected(w)
    }.toVector

    val g = Graph {
      import graph._
      val in        = ValueIntSeq(inData  : _*)
      val winSz     = ValueIntSeq(winSzSq : _*)
      val out       = WindowIndexWhere(in, winSz)
      DebugIntPromise(out, p)
    }

    runGraph(g, 128)

    assert(p.isCompleted)
    val res = getPromiseVec(p)
    assert (res === expected)
  }
}