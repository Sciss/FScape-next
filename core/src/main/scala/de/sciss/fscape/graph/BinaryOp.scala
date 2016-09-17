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

import de.sciss.fscape.stream.{StreamIn, StreamOut}
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

    /** The default converts to `Double`, but specific operators
      * may better preserve semantics and precision for other types such as `Int` and `Long`.
      */
    def apply(a: Constant, b: Constant): Constant = ConstantD(apply(a.doubleValue, b.doubleValue))

    def name: String = plainName.capitalize

    def make(a: GE, b: GE): GE = (a, b) match {
      case (av: Constant, bv: Constant) => ConstantD(apply(av.doubleValue, bv.doubleValue)) // XXX TODO --- possibly preserve number type
      case _ => BinaryOp(op.id, a, b)
    }

    private def plainName: String = {
      val cn = getClass.getName
      val sz = cn.length
      val i  = cn.indexOf('$') + 1
      cn.substring(i, if (cn.charAt(sz - 1) == '$') sz - 1 else sz)
    }
  }

  private def mkIntOrLong(n: Long): Constant =
    if (n >= Int.MinValue && n <= Int.MaxValue)
      ConstantI(n.toInt)
    else
      ConstantL(n)

  case object Plus extends Op {
    final val id = 0
    override val name = "+"

    def apply(a: Double, b: Double) = rd.+(a, b)

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (ConstantL(an), _) => ConstantL(an + b.longValue)
      case (_, ConstantL(bn)) => ConstantL(a.longValue + bn)
      case _ =>
        val an = a.longValue
        val bn = b.longValue
        val n  = an + bn
        mkIntOrLong(n)
    }
  }

  case object Minus extends Op {
    final val id = 1
    override val name = "-"

    def apply(a: Double, b: Double) = rd.-(a, b)

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (ConstantL(an), _) => ConstantL(an - b.longValue)
      case (_, ConstantL(bn)) => ConstantL(a.longValue - bn)
      case _ =>
        val an = a.longValue
        val bn = b.longValue
        val n  = an - bn
        mkIntOrLong(n)
    }
  }

  case object Times extends Op {
    final val id = 2
    override val name = "*"

    override def make(a: GE, b: GE): GE =
      (a, b) match {
      case (Constant(0), _)  => a
      case (_, Constant(0))  => b
      case (Constant(1), _)  => b
      case (_, Constant(1))  => a
      case (Constant(-1), _) => UnaryOp.Neg.make(b) // -b
      case (_, Constant(-1)) => UnaryOp.Neg.make(a) // -a
      case _                 => super.make(a, b)
    }

    def apply(a: Double, b: Double) = rd.*(a, b)

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (ConstantL(an), _) => ConstantL(an * b.longValue)  // XXX TODO --- check overflow
      case (_, ConstantL(bn)) => ConstantL(a.longValue * bn)  // XXX TODO --- check overflow
      case _ =>
        val an = a.longValue
        val bn = b.longValue
        val n  = an * bn
        mkIntOrLong(n)
    }
  }

  // case object IDiv           extends Op(  3 )
  case object Div extends Op {
    final val id = 4
    override val name = "/"

    def apply(a: Double, b: Double) = rd./(a, b)

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (_: ConstantL, _) | (_, _: ConstantL) =>
        val an = a.longValue
        val bn = b.longValue
        if (an % bn == 0)
          ConstantL(an / bn)
        else
          ConstantD(an.toDouble / bn.toDouble)

      case _ =>
        val ai = a.intValue
        val bi = b.intValue
        if (ai % bi == 0)
          ConstantI(ai / bi)
        else
          ConstantD(ai.toDouble / bi.toDouble)
    }
  }

  case object Mod extends Op {
    final val id = 5
    override val name = "%"

    def apply(a: Double, b: Double): Double = rd.%(a, b)

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (_: ConstantL, _) | (_, _: ConstantL) =>
        val an = a.longValue
        val bn = b.longValue
        ConstantL(an % bn)
      case _ =>
        val ai = a.intValue
        val bi = b.intValue
        ConstantI(ai % bi)
    }
  }

  case object Eq extends Op {
    final val id = 6
    override val name = "sig_=="

    def apply(a: Double, b: Double): Double = if (a == b) 1 else 0
    override def apply(a: Constant, b: Constant): Constant = if (a.value == b.value) 1 else 0
  }

  case object Neq extends Op {
    final val id = 7
    override val name = "sig_!="

    def apply(a: Double, b: Double): Double = if (a != b) 1 else 0
    override def apply(a: Constant, b: Constant): Constant = if (a.value != b.value) 1 else 0
  }

  case object Lt extends Op {
    final val id = 8
    override val name = "<"

    def apply(a: Double, b: Double): Double = if (a < b) 1 else 0 // NOT rd.< !

    override def apply(a: Constant, b: Constant): Constant = {
      val res: Boolean = (a, b) match {
        case (ConstantD(ad), ConstantD(bd)) => ad < bd
        case (ConstantD(ad), ConstantL(bn)) => ad < bn
        case (ConstantD(ad), ConstantI(bi)) => ad < bi
        case (ConstantL(an), ConstantD(bd)) => an < bd
        case (ConstantL(an), ConstantL(bn)) => an < bn
        case (ConstantL(an), ConstantI(bi)) => an < bi
        case (ConstantI(ai), ConstantD(bd)) => ai < bd
        case (ConstantI(ai), ConstantL(bn)) => ai < bn
        case (ConstantI(ai), ConstantI(bi)) => ai < bi
      }
      if (res) 1 else 0
    }
  }

  case object Gt extends Op {
    final val id = 9
    override val name = ">"

    def apply(a: Double, b: Double): Double = if (a > b) 1 else 0 // NOT rd.> !

    override def apply(a: Constant, b: Constant): Constant = {
      val res: Boolean = (a, b) match {
        case (ConstantD(ad), ConstantD(bd)) => ad > bd
        case (ConstantD(ad), ConstantL(bn)) => ad > bn
        case (ConstantD(ad), ConstantI(bi)) => ad > bi
        case (ConstantL(an), ConstantD(bd)) => an > bd
        case (ConstantL(an), ConstantL(bn)) => an > bn
        case (ConstantL(an), ConstantI(bi)) => an > bi
        case (ConstantI(ai), ConstantD(bd)) => ai > bd
        case (ConstantI(ai), ConstantL(bn)) => ai > bn
        case (ConstantI(ai), ConstantI(bi)) => ai > bi
      }
      if (res) 1 else 0
    }
  }

  case object Leq extends Op {
    final val id = 10
    override val name = "<="

    def apply(a: Double, b: Double): Double = if (a <= b) 1 else 0 // NOT rd.<= !

    override def apply(a: Constant, b: Constant): Constant = {
      val res: Boolean = (a, b) match {
        case (ConstantD(ad), ConstantD(bd)) => ad <= bd
        case (ConstantD(ad), ConstantL(bn)) => ad <= bn
        case (ConstantD(ad), ConstantI(bi)) => ad <= bi
        case (ConstantL(an), ConstantD(bd)) => an <= bd
        case (ConstantL(an), ConstantL(bn)) => an <= bn
        case (ConstantL(an), ConstantI(bi)) => an <= bi
        case (ConstantI(ai), ConstantD(bd)) => ai <= bd
        case (ConstantI(ai), ConstantL(bn)) => ai <= bn
        case (ConstantI(ai), ConstantI(bi)) => ai <= bi
      }
      if (res) 1 else 0
    }
  }

  case object Geq extends Op {
    final val id = 11
    override val name = ">="

    def apply(a: Double, b: Double): Double = if (a >= b) 1 else 0 // NOT rd.>= !

    override def apply(a: Constant, b: Constant): Constant = {
      val res: Boolean = (a, b) match {
        case (ConstantD(ad), ConstantD(bd)) => ad >= bd
        case (ConstantD(ad), ConstantL(bn)) => ad >= bn
        case (ConstantD(ad), ConstantI(bi)) => ad >= bi
        case (ConstantL(an), ConstantD(bd)) => an >= bd
        case (ConstantL(an), ConstantL(bn)) => an >= bn
        case (ConstantL(an), ConstantI(bi)) => an >= bi
        case (ConstantI(ai), ConstantD(bd)) => ai >= bd
        case (ConstantI(ai), ConstantL(bn)) => ai >= bn
        case (ConstantI(ai), ConstantI(bi)) => ai >= bi
      }
      if (res) 1 else 0
    }
  }

  case object Min extends Op {
    final val id = 12
    def apply(a: Double, b: Double): Double = rd.min(a, b)

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (ConstantL(an), _) => math.min(an, b.longValue)
      case (_, ConstantL(bn)) => math.min(a.longValue, bn)
      case _                  => math.min(a.intValue, b.intValue)
    }
  }

  case object Max extends Op {
    final val id = 13
    def apply(a: Double, b: Double): Double = rd.max(a, b)

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (ConstantL(an), _) => math.max(an, b.longValue)
      case (_, ConstantL(bn)) => math.max(a.longValue, bn)
      case _                  => math.max(a.intValue, b.intValue)
    }
  }

  case object BitAnd extends Op {
    final val id = 14
    override val name = "&"

    def apply(a: Double, b: Double): Double = a.toLong & b.toLong

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (ConstantL(an), _) => an & b.longValue
      case (_, ConstantL(bn)) => a.longValue & bn
      case _                  => a.intValue & b.intValue
    }
  }

  case object BitOr extends Op {
    final val id = 15
    override val name = "|"

    def apply(a: Double, b: Double): Double = a.toLong | b.toLong

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (ConstantL(an), _) => an | b.longValue
      case (_, ConstantL(bn)) => a.longValue | bn
      case _                  => a.intValue | b.intValue
    }
  }

  case object BitXor extends Op {
    final val id = 16
    override val name = "^"

    def apply(a: Double, b: Double): Double = a.toLong ^ b.toLong

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (ConstantL(an), _) => an ^ b.longValue
      case (_, ConstantL(bn)) => a.longValue ^ bn
      case _                  => a.intValue ^ b.intValue
    }
  }

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

    override def apply(a: Constant, b: Constant): Constant = UnaryOp.Abs(Minus(a, b))
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

  // XXX TODO --- refined apply
  case object Clip2 extends Op {
    final val id = 42
    def apply(a: Double, b: Double): Double = rd.clip2(a, b)
  }

  case object Excess extends Op {
    final val id = 43
    def apply(a: Double, b: Double): Double = rd.excess(a, b)
  }

  // XXX TODO --- refined apply
  case object Fold2 extends Op {
    final val id = 44
    def apply(a: Double, b: Double): Double = rd.fold2(a, b)
  }

  // XXX TODO --- refined apply
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

/** A binary operator UGen, for example two sum or multiply two signals.
  * The left or `a` input is "hot", i.e. it keeps the UGen running,
  * while the right or `b` input may close early, and the last value will
  * be remembered.
  *
  * @param op   the identifier of the operator (e.g. `BinaryOp.Times.id`)
  * @param a    the left operand which determines how long the UGen computes
  * @param b    the right operand.
  */
final case class BinaryOp(op: Int, a: GE, b: GE) extends UGenSource.SingleOut {

  protected def makeUGens(implicit builder: UGenGraph.Builder): UGenInLike =
    unwrap(Vector(a.expand, b.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit builder: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, inputs = args, rest = op)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in1, in2) = args
    val op0 = BinaryOp.Op(op)
    stream.BinaryOp(op = op0, in1 = in1.toDouble, in2 = in2.toDouble)
  }
}