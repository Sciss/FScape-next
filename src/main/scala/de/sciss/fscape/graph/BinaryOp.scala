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

package de.sciss.fscape
package graph

import de.sciss.fscape.stream.StreamIn
import de.sciss.numbers.{DoubleFunctions => rd, DoubleFunctions2 => rd2}

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}

object BinaryOp {
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
    op =>

    def id: Int

    def apply(a: Double, b: Double): Double

    def name: String = plainName.capitalize

    final def make(a: GE, b: GE): GE = (a, b) match {
      case (Constant(av), Constant(bv)) => ConstantD(apply(av, bv))
      case _                => new BinaryOp(op, a, b)
    }

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

}
final case class BinaryOp(op: BinaryOp.Op, a: GE, b: GE) extends UGenSource.SingleOut {

  protected def makeUGen(args: Vec[UGenIn])(implicit builder: UGenGraph.Builder): UGenInLike = ???

  protected def makeUGens(implicit builder: UGenGraph.Builder): UGenInLike = ???

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamIn = ???
}