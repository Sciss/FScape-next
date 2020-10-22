package de.sciss.fscape

import de.sciss.kollflitz.Vec
import de.sciss.numbers

import scala.concurrent.Promise

class RangeScaleSpec extends UGenSpec {
  "The LinLin, LinExp, ExpLin, ExpExp UGens" should "work as intended" in {
    for {
      inLen <- Seq(0, 1, 15, 16, 17)
      inLo  <- Seq( 1.0,  0.5)
      inHi  <- Seq(15.4, 22.0)
      outLo <- Seq( 4.0,  6.3)
      outHi <- Seq( 3.2,  7.0)
    } {
      val pLL = Promise[Vec[Double]]()
      val pLE = Promise[Vec[Double]]()
      val pEL = Promise[Vec[Double]]()
      val pEE = Promise[Vec[Double]]()

      val g = Graph {
        import graph._
        val in    = ArithmSeq(start = 1, length = inLen)
        val sigLL = in.linLin(inLo, inHi, outLo, outHi)
        val sigLE = in.linExp(inLo, inHi, outLo, outHi)
        val sigEL = in.expLin(inLo, inHi, outLo, outHi)
        val sigEE = in.expExp(inLo, inHi, outLo, outHi)
        DebugDoublePromise(sigLL, pLL)
        DebugDoublePromise(sigLE, pLE)
        DebugDoublePromise(sigEL, pEL)
        DebugDoublePromise(sigEE, pEE)
      }

      runGraph(g, 16)

      val resLL = getPromiseVec(pLL)
      val resLE = getPromiseVec(pLE)
      val resEL = getPromiseVec(pEL)
      val resEE = getPromiseVec(pEE)
      val inSq  = if (inLen < 1) Vector.empty else (1 to inLen).toVector

      import numbers.Implicits._
      val expLL = inSq.map(_.linLin(inLo, inHi, outLo, outHi))
      val expLE = inSq.map(_.linExp(inLo, inHi, outLo, outHi))
      val expEL = inSq.map(_.expLin(inLo, inHi, outLo, outHi))
      val expEE = inSq.map(_.expExp(inLo, inHi, outLo, outHi))

      val info = s"inLen $inLen, inLo $inLo, inHi $inHi, outLo $outLo, outHi $outHi"
      difOk(resLL, expLL, s"LL $info")
      difOk(resLE, expLE, s"LE $info")
      difOk(resEL, expEL, s"EL $info")
      difOk(resEE, expEE, s"EE $info")
    }
  }
}