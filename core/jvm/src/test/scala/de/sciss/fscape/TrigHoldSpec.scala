package de.sciss.fscape

import de.sciss.fscape.Ops._
import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class TrigHoldSpec extends UGenSpec {
  "The TrigHold UGen" should "work as intended" in {
    val szI = 600
    val szC = 500
    val pI  = 64
    val pC  = 72
    val pH  = 0 to 100 by 20
    val p   = Promise[Vec[Int]]()
    val g   = Graph {
      import graph._
      val in    = Metro(64).take(szI)
      val clear = Metro(72).take(szC)
      val d     = TrigHold(in, length = ValueIntSeq(pH: _*), clear = clear)
      DebugIntPromise(d, p)
    }

    runGraph(g, 512)
    val inSq    = Vector.tabulate(szI)(i => i % pI == 0)
    val clearSq = Vector.tabulate(szC)(i => i % pC == 0).padTo(szI, false)
    var highRem = 0
    var lenIdx  = 0
    val exp     = Vector.tabulate(szI) { i =>
      if (clearSq(i)) highRem = 0
      if (inSq(i)) {
        highRem = if (lenIdx < pH.length) pH(lenIdx) else pH.last
        lenIdx += 1
      }
      val state = if (highRem > 0) 1 else 0
      highRem -= 1
      state
    }

    assert(p.isCompleted)
    val res = getPromiseVec(p)
    assert (res === exp)
  }
}