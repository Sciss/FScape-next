package de.sciss.fscape

import de.sciss.fscape.Ops._
import de.sciss.kollflitz.Vec
import de.sciss.numbers

import scala.concurrent.Promise

class ClipFoldWrapSpec extends UGenSpec {
  sealed trait Op {
    def applyGE(inSq: Vec[Int], lo: Int, hi: Int): GE
    def applySq(inSq: Vec[Int], lo: Int, hi: Int): Vec[Int]

    def applyGE(inSq: Vec[Long], lo: Long, hi: Long): GE
    def applySq(inSq: Vec[Long], lo: Long, hi: Long): Vec[Long]

    def applyGE(inSq: Vec[Double], lo: Double, hi: Double): GE
    def applySq(inSq: Vec[Double], lo: Double, hi: Double): Vec[Double]
  }
  final case object OpClip extends Op {
    import graph._
    import numbers.{DoubleFunctions => rd, IntFunctions => ri, LongFunctions => rl}

    def applyGE(inSq: Vec[Int   ], lo: Int    , hi: Int   ): GE           = ValueIntSeq   (inSq: _*).clip(lo, hi)
    def applySq(inSq: Vec[Int   ], lo: Int    , hi: Int   ): Vec[Int    ] = inSq.map(ri.clip(_, lo, hi))

    def applyGE(inSq: Vec[Long  ], lo: Long   , hi: Long  ): GE           = ValueLongSeq  (inSq: _*).clip(lo, hi)
    def applySq(inSq: Vec[Long  ], lo: Long   , hi: Long  ): Vec[Long   ] = inSq.map(rl.clip(_, lo, hi))

    def applyGE(inSq: Vec[Double], lo: Double , hi: Double): GE           = ValueDoubleSeq(inSq: _*).clip(lo, hi)
    def applySq(inSq: Vec[Double], lo: Double , hi: Double): Vec[Double ] = inSq.map(rd.clip(_, lo, hi))
  }
  final case object OpFold extends Op {
    import graph._
    import numbers.{DoubleFunctions => rd, IntFunctions => ri, LongFunctions => rl}

    def applyGE(inSq: Vec[Int   ], lo: Int    , hi: Int   ): GE           = ValueIntSeq   (inSq: _*).fold(lo, hi)
    def applySq(inSq: Vec[Int   ], lo: Int    , hi: Int   ): Vec[Int    ] = inSq.map(ri.fold(_, lo, hi))

    def applyGE(inSq: Vec[Long  ], lo: Long   , hi: Long  ): GE           = ValueLongSeq  (inSq: _*).fold(lo, hi)
    def applySq(inSq: Vec[Long  ], lo: Long   , hi: Long  ): Vec[Long   ] = inSq.map(rl.fold(_, lo, hi))

    def applyGE(inSq: Vec[Double], lo: Double , hi: Double): GE           = ValueDoubleSeq(inSq: _*).fold(lo, hi)
    def applySq(inSq: Vec[Double], lo: Double , hi: Double): Vec[Double ] = inSq.map(rd.fold(_, lo, hi))
  }
  final case object OpWrap extends Op {
    import graph._
    import numbers.{DoubleFunctions => rd, IntFunctions => ri, LongFunctions => rl}

    def applyGE(inSq: Vec[Int   ], lo: Int    , hi: Int   ): GE           = ValueIntSeq   (inSq: _*).wrap(lo, hi)
    def applySq(inSq: Vec[Int   ], lo: Int    , hi: Int   ): Vec[Int    ] = inSq.map(ri.wrap(_, lo, hi))

    def applyGE(inSq: Vec[Long  ], lo: Long   , hi: Long  ): GE           = ValueLongSeq  (inSq: _*).wrap(lo, hi)
    def applySq(inSq: Vec[Long  ], lo: Long   , hi: Long  ): Vec[Long   ] = inSq.map(rl.wrap(_, lo, hi))

    def applyGE(inSq: Vec[Double], lo: Double , hi: Double): GE           = ValueDoubleSeq(inSq: _*).wrap(lo, hi)
    def applySq(inSq: Vec[Double], lo: Double , hi: Double): Vec[Double ] = inSq.map(rd.wrap(_, lo, hi))
  }
  
  "The Clip, Fold, and Wrap UGens" should "work as intended" in {
    for {
      op  <- Seq(OpClip, OpFold, OpWrap)
      loD <- Seq(-3.0, -1.5, -0.5, 0.5, 1.5)
      hiD <- Seq(-1.4, -0.4, 0.6, 1.6, 3.1)
    } {
      import graph._
      val pI    = Promise[Vec[Any]]()
      val pL    = Promise[Vec[Any]]()
      val pD    = Promise[Vec[Any]]()
      val sqI   = -10 to +10
      val sqL   = sqI.map(_.toLong)
      val sqD   = sqI.map(_.toDouble + 0.1)
      val loI   = loD.toInt
      val loL   = loD.toLong
      val hiI   = hiD.toInt
      val hiL   = hiD.toLong
      val expD  = op.applySq(sqD, loD, hiD)
      val expI  = op.applySq(sqI, loI, hiI)
      val expL  = op.applySq(sqL, loL, hiL)

      val g = Graph {
        val inD: GE = op.applyGE(sqD, loD, hiD)
        val inI: GE = op.applyGE(sqI, loI, hiI)
        val inL: GE = op.applyGE(sqL, loL, hiL)
        DebugAnyPromise(inD, pD)
        DebugAnyPromise(inI, pI)
        DebugAnyPromise(inL, pL)
      }

      runGraph(g)
      val obsD = getPromiseVec(pD)
      assert (expD === obsD, s"For $op, loD $loD, hiD $hiD")
      val obsI = getPromiseVec(pI)
      assert (expI === obsI, s"For $op, loI $loI, hiI $hiI")
      val obsL = getPromiseVec(pL)
      assert (expL === obsL, s"For $op, loL $loL, hiL $hiL")
    }
  }
}