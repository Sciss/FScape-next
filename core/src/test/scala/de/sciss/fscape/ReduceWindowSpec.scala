package de.sciss.fscape

import de.sciss.fscape.graph.BinaryOp
import de.sciss.kollflitz.Vec

import scala.annotation.tailrec
import scala.concurrent.Promise

class ReduceWindowSpec extends UGenSpec {
  "The ReduceWindow UGen" should "work as intended" in {
    import BinaryOp.{BitAnd, Max, Min, Plus, Times}

    val opSq = Seq(BitAnd, Max, Min, Plus, Times)

    val rnd         = new util.Random(2L)
    val winSzSq     = List.fill(40)(rnd.nextInt(10) + 1)  // don't test zero for now
    val inLen       = winSzSq.sum // 385
    val inData      = Vector.fill(inLen)(rnd.nextDouble())

    val inDataSq: Seq[Vector[Double]] = {
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

    for {
      op <- opSq
    } {
      // println(s"inDataSq $inDataSq")
      val expected: Vector[Double] = inDataSq.iterator.map { w =>
        w.reduce(op(_, _))
      }.toVector

      val p = Promise[Vec[Double]]()
      val g = Graph {
        import graph._
        val in    = ValueDoubleSeq(inData  : _*)
        val winSz = ValueIntSeq   (winSzSq : _*)
        val out   = ReduceWindow(in, winSz, op = op.id)
        DebugDoublePromise(out, p)
      }

      runGraph(g, 128)

      assert(p.isCompleted)
      val res = getPromiseVec(p)
      assert (res === expected)
    }
  }
}