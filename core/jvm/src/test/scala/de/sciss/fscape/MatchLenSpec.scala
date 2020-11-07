package de.sciss.fscape

import de.sciss.fscape.Ops._
import de.sciss.kollflitz.Vec

import scala.concurrent.Promise
import scala.util.Success

class MatchLenSpec extends UGenSpec {
  "The .matchLen op" should "work as specified" in {
    val pairs = List(
      (10, 17),
      (0, 10),
      (10, 10),
      (10, 3000),
      (1, 3000)
    )

    pairs.foreach { case (n1, n2) =>
      val exp1  = Vector.fill(n1)(1).padTo(n2, 0)
      val exp2  = if (n1 > 0) Vector.fill(n2)(2).take(n1) else Vector(-1)
      val p1    = Promise[Vec[Int]]()
      val p2    = Promise[Vec[Int]]()

      val g = Graph {
        import graph._
        val x1    = DC(1).take(n1)
        val x2    = DC(2).take(n2)
        val sig12 = x1.matchLen(x2)
        val sig21 = x2.matchLen(x1)
        // DebugIntPromise yields `Failure` if signal is empty; use code -1 instead
        DebugIntPromise(if (n2 > 0) sig12 else -1, p1)
        DebugIntPromise(if (n1 > 0) sig21 else -1, p2)
      }

      runGraph(g)

      assert(p1.isCompleted)
      assert(p2.isCompleted)
      val res1 = p1.future.value.get
      val res2 = p2.future.value.get
      assert (res1 === Success(exp1), s"$n1 match $n2")
      assert (res2 === Success(exp2), s"$n2 match $n1")
    }
  }
}