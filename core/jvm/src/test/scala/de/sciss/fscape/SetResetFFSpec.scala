package de.sciss.fscape

import de.sciss.fscape.Ops._
import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class SetResetFFSpec extends UGenSpec {
  "The SetResetFF UGen" should "work as intended" in {
    val sz  = 600
    val p1  = 64
    val p2  = 72
    val p   = Promise[Vec[Int]]()
    val g   = Graph {
      import graph._
      val set   = Metro(64).take(sz)
      val reset = Metro(72).take(sz)
      val d     = SetResetFF(set, reset)
      DebugIntPromise(d, p)
    }

    runGraph(g, 512)
    val setSq   = Vector.tabulate(sz)(i => i % p1 == 0)
    val resetSq = Vector.tabulate(sz)(i => i % p2 == 0)
    val exp = (0 until sz).scanLeft(0)((x, i) => if (resetSq(i)) 0 else if (setSq(i)) 1 else x).tail

    assert(p.isCompleted)
    val res = getPromiseVec(p)
    assert (res === exp)
  }
}