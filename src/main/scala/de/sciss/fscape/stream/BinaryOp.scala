/*
 *  BinaryOp.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.scaladsl.GraphDSL
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.stream.impl.FilterIn2Impl
import de.sciss.numbers.{DoubleFunctions => rd, DoubleFunctions2 => rd2}

import scala.annotation.switch

object BinaryOp {
  def apply(op: Op, in1: Outlet[BufD], in2: Outlet[BufD])
           (implicit builder: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] = {
    val stage0  = new Stage(op)
    val stage   = builder.add(stage0)
    import GraphDSL.Implicits._
    in1 ~> stage.in0
    in2 ~> stage.in1
    stage.out
  }

  object Op {
    def apply(id: Int): Op = (id: @switch) match {
      case Plus     .id => Plus
      case Minus    .id => Minus
      case Times    .id => Times
      case Div      .id => Div
      case Mod      .id => Mod
      case Eq       .id => Eq
      case Neq      .id => Neq
      case Lt       .id => Lt
      case Gt       .id => Gt
      case Leq      .id => Leq
      case Geq      .id => Geq
      case Min      .id => Min
      case Max      .id => Max
//      case BitAnd   .id => BitAnd
//      case BitOr    .id => BitOr
//      case BitXor   .id => BitXor
      case RoundTo  .id => RoundTo
      case RoundUpTo.id => RoundUpTo
      case Trunc    .id => Trunc
      case Atan2    .id => Atan2
      case Hypot    .id => Hypot
      case Hypotx   .id => Hypotx
      case Pow      .id => Pow
      case Ring1    .id => Ring1
      case Ring2    .id => Ring2
      case Ring3    .id => Ring3
      case Ring4    .id => Ring4
      case Difsqr   .id => Difsqr
      case Sumsqr   .id => Sumsqr
      case Sqrsum   .id => Sqrsum
      case Sqrdif   .id => Sqrdif
      case Absdif   .id => Absdif
      case Thresh   .id => Thresh
      case Amclip   .id => Amclip
      case Scaleneg .id => Scaleneg
      case Clip2    .id => Clip2
      case Excess   .id => Excess
      case Fold2    .id => Fold2
      case Wrap2    .id => Wrap2
//      case Firstarg .id => Firstarg
    }
  }

  sealed trait Op {
    def id: Int
    
    def apply(a: Double, b: Double): Double

    def name: String = plainName.capitalize

    private def plainName: String = {
      val cn = getClass.getName
      val sz = cn.length
      val i  = cn.indexOf('$') + 1
      cn.substring(i, if (cn.charAt(sz - 1) == '$') sz - 1 else sz)
    }
  }

  case object Plus extends Op {
    final val id = 0
    override val name = "+"

    def apply(a: Double, b: Double) = rd.+(a, b)
  }

  case object Minus extends Op {
    final val id = 1
    override val name = "-"

    def apply(a: Double, b: Double) = rd.-(a, b)
  }

  case object Times extends Op {
    final val id = 2
    override val name = "*"

    def apply(a: Double, b: Double) = rd.*(a, b)
  }

  // case object IDiv           extends Op(  3 )
  case object Div extends Op {
    final val id = 4
    override val name = "/"

    def apply(a: Double, b: Double) = rd./(a, b)
  }

  case object Mod extends Op {
    final val id = 5
    override val name = "%"

    def apply(a: Double, b: Double): Double = rd.%(a, b)
  }

  case object Eq extends Op {
    final val id = 6
    override val name = "sig_=="

    def apply(a: Double, b: Double): Double = if (a == b) 1 else 0
  }

  case object Neq extends Op {
    final val id = 7
    override val name = "sig_!="

    def apply(a: Double, b: Double): Double = if (a != b) 1 else 0
  }

  case object Lt extends Op {
    final val id = 8
    override val name = "<"

    def apply(a: Double, b: Double): Double = if (a < b) 1 else 0 // NOT rd.< !
  }

  case object Gt extends Op {
    final val id = 9
    override val name = ">"

    def apply(a: Double, b: Double): Double = if (a > b) 1 else 0 // NOT rd.> !
  }

  case object Leq extends Op {
    final val id = 10
    override val name = "<="

    def apply(a: Double, b: Double): Double = if (a <= b) 1 else 0 // NOT rd.<= !
  }

  case object Geq extends Op {
    final val id = 11
    override val name = ">="

    def apply(a: Double, b: Double): Double = if (a >= b) 1 else 0 // NOT rd.>= !
  }

  case object Min extends Op {
    final val id = 12
    def apply(a: Double, b: Double): Double = rd.min(a, b)
  }

  case object Max extends Op {
    final val id = 13
    def apply(a: Double, b: Double): Double = rd.max(a, b)
  }

//  case object BitAnd extends Op {
//    final val id = 14
//    override val name = "&"
//
//    def apply(a: Double, b: Double): Double = rd.&(a, b)
//  }
//
//  case object BitOr extends Op {
//    final val id = 15
//    override val name = "|"
//
//    def apply(a: Double, b: Double): Double = rd.|(a, b)
//  }
//
//  case object BitXor extends Op {
//    final val id = 16
//    override val name = "^"
//
//    def apply(a: Double, b: Double): Double = rd.^(a, b)
//  }

  // case object Lcm            extends Op( 17 )
  // case object Gcd            extends Op( 18 )
  case object RoundTo extends Op {
    final val id = 19
    def apply(a: Double, b: Double) = rd.roundTo(a, b)
  }

  case object RoundUpTo extends Op {
    final val id = 20
    def apply(a: Double, b: Double): Double = rd.roundUpTo(a, b)
  }

  case object Trunc extends Op {
    final val id = 21
    def apply(a: Double, b: Double): Double = rd.trunc(a, b)
  }

  case object Atan2 extends Op {
    final val id = 22
    def apply(a: Double, b: Double): Double = rd.atan2(a, b)
  }

  case object Hypot extends Op {
    final val id = 23
    def apply(a: Double, b: Double): Double = rd.hypot(a, b)
  }

  case object Hypotx extends Op {
    final val id = 24
    def apply(a: Double, b: Double): Double = rd.hypotx(a, b)
  }

  /** '''Warning:''' Unlike a normal power operation, the signum of the
    * left operand is always preserved. I.e. `DC.kr(-0.5).pow(2)` will
    * not output `0.25` but `-0.25`. This is to avoid problems with
    * floating point noise and negative input numbers, so
    * `DC.kr(-0.5).pow(2.001)` does not result in a `NaN`, for example.
    */
  case object Pow extends Op {
    final val id = 25
    def apply(a: Double, b: Double): Double = rd.pow(a, b)
  }

  // case object <<             extends Op( 26 )
  // case object >>             extends Op( 27 )
  // case object UnsgnRghtShft  extends Op( 28 )
  // case object Fill           extends Op( 29 )
  case object Ring1 extends Op {
    final val id = 30
    def apply(a: Double, b: Double): Double = rd2.ring1(a, b)
  }

  case object Ring2 extends Op {
    final val id = 31
    def apply(a: Double, b: Double): Double = rd2.ring2(a, b)
  }

  case object Ring3 extends Op {
    final val id = 32
    def apply(a: Double, b: Double) = rd2.ring3(a, b)
  }

  case object Ring4 extends Op {
    final val id = 33
    def apply(a: Double, b: Double): Double = rd2.ring4(a, b)
  }

  case object Difsqr extends Op {
    final val id = 34
    def apply(a: Double, b: Double): Double = rd.difsqr(a, b)
  }

  case object Sumsqr extends Op {
    final val id = 35
    def apply(a: Double, b: Double): Double = rd.sumsqr(a, b)
  }

  case object Sqrsum extends Op {
    final val id = 36
    def apply(a: Double, b: Double): Double = rd.sqrsum(a, b)
  }

  case object Sqrdif extends Op {
    final val id = 37
    def apply(a: Double, b: Double): Double = rd.sqrdif(a, b)
  }

  case object Absdif extends Op {
    final val id = 38
    def apply(a: Double, b: Double): Double = rd.absdif(a, b)
  }

  case object Thresh extends Op {
    final val id = 39
    def apply(a: Double, b: Double): Double = rd2.thresh(a, b)
  }

  case object Amclip extends Op {
    final val id = 40
    def apply(a: Double, b: Double): Double = rd2.amclip(a, b)
  }

  case object Scaleneg extends Op {
    final val id = 41
    def apply(a: Double, b: Double): Double = rd2.scaleneg(a, b)
  }

  case object Clip2 extends Op {
    final val id = 42
    def apply(a: Double, b: Double): Double = rd.clip2(a, b)
  }

  case object Excess extends Op {
    final val id = 43
    def apply(a: Double, b: Double): Double = rd.excess(a, b)
  }

  case object Fold2 extends Op {
    final val id = 44
    def apply(a: Double, b: Double): Double = rd.fold2(a, b)
  }

  case object Wrap2 extends Op {
    final val id = 45
    def apply(a: Double, b: Double): Double = rd.wrap2(a, b)
  }

//  case object Firstarg extends Op {
//    final val id = 46
//
//    def apply(a: Double, b: Double) = a
//  }

  // case object Rrand          extends Op( 47 )
  // case object ExpRRand       extends Op( 48 )

  private final class Stage(op: Op)(implicit ctrl: Control)
    extends GraphStage[FanInShape2[BufD, BufD, BufD]] {

    val shape = new FanInShape2(
      in0 = Inlet [BufD]("BinaryOp.a"  ),
      in1 = Inlet [BufD]("BinaryOp.b"  ),
      out = Outlet[BufD]("BinaryOp.out")
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(op, shape)
  }

  private final class Logic(op: Op,
                            protected val shape: FanInShape2[BufD, BufD, BufD])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with FilterIn2Impl[BufD, BufD, BufD] {

    private[this] var inOff             = 0  // regarding `bufIn`
    private[this] var inRemain          = 0
    private[this] var outOff            = 0  // regarding `bufOut`
    private[this] var outRemain         = 0
    private[this] var outSent           = true
    private[this] var bVal: Double      = _

    @inline
    private[this] def allocOutBuf(): BufD = ctrl.borrowBufD()

    @inline
    private[this] def shouldRead = inRemain == 0 && canRead

    protected def process(): Unit = {
      var stateChange = false

      if (shouldRead) {
        readIns()
        inRemain    = bufIn0.size
        inOff       = 0
        stateChange = true
      }

      if (outSent) {
        bufOut        = allocOutBuf()
        outRemain     = bufOut.size
        outOff        = 0
        outSent       = false
        stateChange   = true
      }

      val chunk = math.min(inRemain, outRemain)
      if (chunk > 0) {
        var inOffI  = inOff
        var outOffI = outOff
        val aStop   = inOffI + chunk
        val a       = bufIn0.buf
        val b       = if (bufIn1 == null) null else bufIn1.buf
        val out     = bufOut.buf
        val bStop   = if (b == null) 0 else bufIn1.size
        var bv      = bVal
        while (inOffI < aStop) {
          val                 av = a(inOffI)
          if (inOffI < bStop) bv = b(inOffI)
          out(outOffI) = op(av, bv)
          inOffI  += 1
          outOffI += 1
        }
        bVal         = bv
        inOff        = inOffI
        inRemain    -= chunk
        outOff       = outOffI
        outRemain   -= chunk
        stateChange  = true
      }

      val flushOut = inRemain == 0 && isClosed(shape.in0)
      if (!outSent && (outRemain == 0 || flushOut) && isAvailable(shape.out)) {
        if (outOff > 0) {
          bufOut.size = outOff
          push(shape.out, bufOut)
        } else {
          bufOut.release()
        }
        bufOut      = null
        outSent     = true
        stateChange = true
      }

      if      (flushOut && outSent) completeStage()
      else if (stateChange)         process()
    }
  }
}