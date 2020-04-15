/*
 *  BinaryOp.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{BufD, BufI, BufL, StreamIn, StreamOut}
import de.sciss.numbers.{DoubleFunctions => rd, DoubleFunctions2 => rd2, LongFunctions => rl}

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}

object BinaryOp {
  object Op {
    def apply(id: Int): Op = (id: @switch) match {
      case Plus               .id => Plus
      case Minus              .id => Minus
      case Times              .id => Times
      case Div                .id => Div
      case Mod                .id => Mod
      case Eq                 .id => Eq
      case Neq                .id => Neq
      case Lt                 .id => Lt
      case Gt                 .id => Gt
      case Leq                .id => Leq
      case Geq                .id => Geq
      case Min                .id => Min
      case Max                .id => Max
      case And                .id => And
      case Or                 .id => Or
      case Xor                .id => Xor
      case Lcm                .id => Lcm
      case Gcd                .id => Gcd
      case RoundTo            .id => RoundTo
      case RoundUpTo          .id => RoundUpTo
      case Trunc              .id => Trunc
      case Atan2              .id => Atan2
      case Hypot              .id => Hypot
      case Hypotx             .id => Hypotx
      case Pow                .id => Pow
      case LeftShift          .id => LeftShift
      case RightShift         .id => RightShift
      case UnsignedRightShift .id => UnsignedRightShift
      case Ring1              .id => Ring1
      case Ring2              .id => Ring2
      case Ring3              .id => Ring3
      case Ring4              .id => Ring4
      case Difsqr             .id => Difsqr
      case Sumsqr             .id => Sumsqr
      case Sqrsum             .id => Sqrsum
      case Sqrdif             .id => Sqrdif
      case Absdif             .id => Absdif
      case Thresh             .id => Thresh
      case Amclip             .id => Amclip
      case Scaleneg           .id => Scaleneg
      case Clip2              .id => Clip2
      case Excess             .id => Excess
      case Fold2              .id => Fold2
      case Wrap2              .id => Wrap2
      case FirstArg           .id => FirstArg
      case SecondArg          .id => SecondArg
    }

    final val MinId = Plus      .id
    final val MaxId = SecondArg .id
  }

  sealed trait Op {
    op =>

    def id: Int

    def funDD: (Double, Double) => Double

    /** The default converts to `Double`, but specific operators
      * may better preserve semantics and precision for other types such as `Int` and `Long`.
      */
    def apply(a: Constant, b: Constant): Constant =
      ConstantD(funDD(a.doubleValue, b.doubleValue))

    def name: String = plainName.capitalize

//    protected[fscape] def make1(a: UGenIn, b: UGenIn): UGenIn

    def make(a: GE, b: GE): GE = (a, b) match {
      case (av: Constant, bv: Constant) => apply(av, bv)
      case _ => BinaryOp(op.id, a, b)
    }

    private def plainName: String = {
      val cn = getClass.getName
      val sz = cn.length
      val i  = cn.indexOf('$') + 1
      cn.substring(i, if (cn.charAt(sz - 1) == '$') sz - 1 else sz)
    }
  }

  sealed trait OpII extends Op {
    def funII: (Int, Int) => Int

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantI(av), ConstantI(bv)) => ConstantI(funII(av, bv))
      case _                              => ConstantD(funDD(a.doubleValue, b.doubleValue))
    }
  }

  sealed trait OpLL extends Op {
    def funLL: (Long, Long) => Long

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantL(av), ConstantL(bv)) => ConstantL(funLL(av, bv))
      case _                              => ConstantD(funDD(a.doubleValue, b.doubleValue))
    }
  }

  sealed trait OpDI extends Op {
    def funDI: (Double, Double) => Int
  }

  sealed trait OpDL extends Op {
    def funDL: (Double, Double) => Long
  }

  sealed trait OpID extends Op {
    def funID: (Int, Int) => Double
  }

  sealed trait OpLD extends Op {
    def funLD: (Long, Long) => Double
  }

  sealed trait OpIL extends Op {
    def funIL: (Int, Int) => Long
  }

  sealed trait OpLI extends Op {
    def funLI: (Long, Long) => Int
  }

  sealed trait OpSame extends Op with OpII with OpLL {
    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(av), ConstantD(bv)) => ConstantD(funDD(av, bv))
      case (ConstantI(av), ConstantI(bv)) => ConstantI(funII(av, bv))
      case (ConstantL(av), ConstantL(bv)) => ConstantL(funLL(av, bv))
    }
  }

  private def mkIntOrLong(n: Long): Constant =
    if (n >= Int.MinValue && n <= Int.MaxValue)
      ConstantI(n.toInt)
    else
      ConstantL(n)

  case object Plus extends Op { // OpSame
    final val id = 0

    override val name = "+"

    override def make(a: GE, b: GE): GE =
      (a, b) match {
        case (Constant(0), _)  => b
        case (_, Constant(0))  => a
        case _                 => super.make(a, b)
      }

    val funDD: (Double, Double) => Double = (a, b) => rd.+(a, b)

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

  case object Minus extends Op {  // OpSame
    final val id = 1

    override val name = "-"

    override def make(a: GE, b: GE): GE =
      (a, b) match {
        case (Constant(0), _)  => b
        case (_, Constant(0))  => a
        case _                 => super.make(a, b)
      }

    val funDD: (Double, Double) => Double = (a, b) => rd.-(a, b)

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

  case object Times extends Op {  // OpSame
    final val id = 2

    override val name = "*"

    override def make(a: GE, b: GE): GE =
      (a, b) match {
          // N.B. do not replace by Constant(0), because lengths might differ!
//      case (Constant(0), _)  => a
//      case (_, Constant(0))  => b
      case (Constant(1), _)  => b
      case (_, Constant(1))  => a
      case (Constant(-1), _) => UnaryOp.Neg.make(b) // -b
      case (_, Constant(-1)) => UnaryOp.Neg.make(a) // -a
      case _                 => super.make(a, b)
    }

    val funDD: (Double, Double) => Double = (a, b) => rd.*(a, b)

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
  case object Div extends Op {  // OpSame
    final val id = 4

    override val name = "/"

    val funDD: (Double, Double) => Double = (a, b) => rd./(a, b)

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

  case object Mod extends Op {  // OpSame
    final val id = 5

    override val name = "%"

    val funDD: (Double, Double) => Double = (a, b) => rd.%(a, b)

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

  case object Eq extends Op { // OpSame
    final val id = 6

    override val name = "sig_=="

    val funDD: (Double, Double) => Double = (a, b) => if (a == b) 1 else 0

    override def apply(a: Constant, b: Constant): Constant = if (a.value == b.value) 1 else 0
  }

  case object Neq extends Op {  // OpSame
    final val id = 7

    override val name = "sig_!="

    val funDD: (Double, Double) => Double = (a, b) => if (a != b) 1 else 0

    override def apply(a: Constant, b: Constant): Constant = if (a.value != b.value) 1 else 0
  }

  case object Lt extends Op { // DI, II, LI
    final val id = 8

    override val name = "<"

    val funDD: (Double, Double) => Double = (a, b) => if (a < b) 1 else 0 // NOT rd.< !

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

  case object Gt extends Op { // DI, LI, II
    final val id = 9

    override val name = ">"

    val funDD: (Double, Double) => Double = (a, b) => if (a > b) 1 else 0 // NOT rd.> !

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

  case object Leq extends Op {  // DI, LI, II
    final val id = 10

    override val name = "<="

    val funDD: (Double, Double) => Double = (a, b) => if (a <= b) 1 else 0 // NOT rd.<= !

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

  case object Geq extends Op {  // DI, LI, II
    final val id = 11

    override val name = ">="

    val funDD: (Double, Double) => Double = (a, b) => if (a >= b) 1 else 0 // NOT rd.>= !

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

  case object Min extends Op {  // OpSame
    final val id = 12

    val funDD: (Double, Double) => Double = (a, b) => rd.min(a, b)

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (ConstantL(an), _) => math.min(an, b.longValue)
      case (_, ConstantL(bn)) => math.min(a.longValue, bn)
      case _                  => math.min(a.intValue, b.intValue)
    }
  }

  case object Max extends Op {  // OpSame
    final val id = 13

    val funDD: (Double, Double) => Double = (a, b) => rd.max(a, b)

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (ConstantL(an), _) => math.max(an, b.longValue)
      case (_, ConstantL(bn)) => math.max(a.longValue, bn)
      case _                  => math.max(a.intValue, b.intValue)
    }
  }

  case object And extends Op { // OpSame
    final val id = 14

    override val name = "&"

    val funDD: (Double, Double) => Double = (a, b) => a.toLong & b.toLong

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (ConstantL(an), _) => an & b.longValue
      case (_, ConstantL(bn)) => a.longValue & bn
      case _                  => a.intValue & b.intValue
    }
  }

  case object Or extends Op {  // OpSame
    final val id = 15

    override val name = "|"

    val funDD: (Double, Double) => Double = (a, b) => a.toLong | b.toLong

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (ConstantL(an), _) => an | b.longValue
      case (_, ConstantL(bn)) => a.longValue | bn
      case _                  => a.intValue | b.intValue
    }
  }

  case object Xor extends Op { // OpSame
    final val id = 16

    override val name = "^"

    val funDD: (Double, Double) => Double = (a, b) => a.toLong ^ b.toLong

    override def apply(a: Constant, b: Constant): Constant = (a, b) match {
      case (ConstantD(_), _)  => super.apply(a, b)
      case (_, ConstantD(_))  => super.apply(a, b)
      case (ConstantL(an), _) => an ^ b.longValue
      case (_, ConstantL(bn)) => a.longValue ^ bn
      case _                  => a.intValue ^ b.intValue
    }
  }

  case object Lcm extends Op {  // OpSame
    final val id = 17

    val funDD: (Double, Double) => Double = (a, b) => rl.lcm(a.toLong, b.toLong).toDouble
  }

  case object Gcd extends Op {  // OpSame
    final val id = 18

    val funDD: (Double, Double) => Double = (a, b) => rl.gcd(a.toLong, b.toLong).toDouble
  }

  case object RoundTo extends Op {  // OpSame
    final val id = 19

    val funDD: (Double, Double) => Double = (a, b) => rd.roundTo(a, b)
  }

  case object RoundUpTo extends Op {  // OpSame
    final val id = 20

    val funDD: (Double, Double) => Double = (a, b) => rd.roundUpTo(a, b)
  }

  case object Trunc extends Op {
    final val id = 21

    val funDD: (Double, Double) => Double = (a, b) => rd.trunc(a, b)
  }

  case object Atan2 extends Op {
    final val id = 22

    val funDD: (Double, Double) => Double = (a, b) => rd.atan2(a, b)
  }

  case object Hypot extends Op {
    final val id = 23

    val funDD: (Double, Double) => Double = (a, b) => rd.hypot(a, b)
  }

  case object Hypotx extends Op {
    final val id = 24

    val funDD: (Double, Double) => Double = (a, b) => rd.hypotApx(a, b)
  }

  /** '''Warning:''' Unlike a normal power operation, the signum of the
    * left operand is always preserved. I.e. `DC.kr(-0.5).pow(2)` will
    * not output `0.25` but `-0.25`. This is to avoid problems with
    * floating point noise and negative input numbers, so
    * `DC.kr(-0.5).pow(2.001)` does not result in a `NaN`, for example.
    */
  case object Pow extends Op {
    final val id = 25

    val funDD: (Double, Double) => Double = (a, b) => rd.pow(a, b)
  }

  case object LeftShift extends Op {  // OpSame
    final val id = 26

    val funDD: (Double, Double) => Double = (a, b) => (a.toLong << b.toLong).toDouble
  }

  case object RightShift extends Op {  // OpSame
    final val id = 27

    val funDD: (Double, Double) => Double = (a, b) => (a.toLong >> b.toLong).toDouble
  }

  case object UnsignedRightShift extends Op {  // OpSame
    final val id = 28

    val funDD: (Double, Double) => Double = (a, b) => (a.toLong >>> b.toLong).toDouble
  }

  // case object Fill           extends Op( 29 )
  case object Ring1 extends Op {
    final val id = 30

    val funDD: (Double, Double) => Double = (a, b) => rd2.ring1(a, b)
  }

  case object Ring2 extends Op {
    final val id = 31

    val funDD: (Double, Double) => Double = (a, b) => rd2.ring2(a, b)
  }

  case object Ring3 extends Op {
    final val id = 32

    val funDD: (Double, Double) => Double = (a, b) => rd2.ring3(a, b)
  }

  case object Ring4 extends Op {
    final val id = 33

    val funDD: (Double, Double) => Double = (a, b) => rd2.ring4(a, b)
  }

  case object Difsqr extends Op { // IL, LL
    final val id = 34

    val funDD: (Double, Double) => Double = (a, b) => rd.difSqr(a, b)
  }

  case object Sumsqr extends Op { // IL, LL
    final val id = 35

    val funDD: (Double, Double) => Double = (a, b) => rd.sumSqr(a, b)
  }

  case object Sqrsum extends Op { // IL, LL
    final val id = 36

    val funDD: (Double, Double) => Double = (a, b) => rd.sqrSum(a, b)
  }

  case object Sqrdif extends Op { // IL, LL
    final val id = 37

    val funDD: (Double, Double) => Double = (a, b) => rd.sqrDif(a, b)
  }

  case object Absdif extends Op { // OpSame
    final val id = 38

    val funDD: (Double, Double) => Double = (a, b) => rd.absDif(a, b)
  }

  case object Thresh extends Op {
    final val id = 39

    val funDD: (Double, Double) => Double = (a, b) => rd2.thresh(a, b)
  }

  case object Amclip extends Op {
    final val id = 40

    val funDD: (Double, Double) => Double = (a, b) => rd2.amClip(a, b)
  }

  case object Scaleneg extends Op {
    final val id = 41

    val funDD: (Double, Double) => Double = (a, b) => rd2.scaleNeg(a, b)
  }

  case object Clip2 extends Op {  // OpSame
    final val id = 42

    val funDD: (Double, Double) => Double = (a, b) => rd.clip2(a, b)
  }

  case object Excess extends Op { // OpSame
    final val id = 43

    val funDD: (Double, Double) => Double = (a, b) => rd.excess(a, b)
  }

  case object Fold2 extends Op {  // OpSame
    final val id = 44

    val funDD: (Double, Double) => Double = (a, b) => rd.fold2(a, b)
  }

  case object Wrap2 extends Op { // OpSame
    final val id = 45

    val funDD: (Double, Double) => Double = (a, b) => rd.wrap2(a, b)
  }

    case object FirstArg extends Op { // OpSame
      final val id = 46

      val funDD: (Double, Double) => Double = (a, _) => a

      override def apply(a: Constant, b: Constant): Constant = a
    }

  // case object Rrand          extends Op( 47 )
  // case object ExpRRand       extends Op( 48 )

  case object SecondArg extends Op {  // OpSame
    final val id = 100

    val funDD: (Double, Double) => Double = (_, b) => b

    override def apply(a: Constant, b: Constant): Constant = b
  }
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
    unwrap(this, Vector(a.expand, b.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit builder: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, inputs = args, adjuncts = Adjunct.Int(op) :: Nil)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in1, in2) = args
    val op0 = BinaryOp.Op(op)

    if (in1.isDouble || in2.isDouble) {
      op0 match {
        case opDI: BinaryOp.OpDI =>
          stream.BinaryOp[Double , BufD, Int   , BufI](op0.name, opDI.funDI, in1 = in1.toDouble, in2 = in2.toDouble ): StreamOut

        case opDL: BinaryOp.OpDL =>
          stream.BinaryOp[Double , BufD, Long  , BufL](op0.name, opDL.funDL, in1 = in1.toDouble , in2 = in2.toDouble): StreamOut

        case _ =>
          stream.BinaryOp[Double , BufD, Double, BufD](op0.name, op0 .funDD, in1 = in1.toDouble , in2 = in2.toDouble): StreamOut
      }
    } else if (in1.isLong || in2.isLong) {
      op0 match {
        case opLI: BinaryOp.OpLI =>
          stream.BinaryOp[Long   , BufL, Int   , BufI](op0.name, opLI.funLI, in1 = in1.toLong   , in2 = in2.toLong  ): StreamOut

        case opLL: BinaryOp.OpLL =>
          stream.BinaryOp[Long   , BufL, Long  , BufL](op0.name, opLL.funLL, in1 = in1.toLong   , in2 = in2.toLong  ): StreamOut

        case opLD: BinaryOp.OpLD =>
          stream.BinaryOp[Long   , BufL, Double, BufD](op0.name, opLD.funLD, in1 = in1.toLong   , in2 = in2.toLong  ): StreamOut

        case _ =>
          stream.BinaryOp[Double , BufD, Double, BufD](op0.name, op0 .funDD, in1 = in1.toDouble , in2 = in2.toDouble): StreamOut
      }

    } else {
      assert (in1.isInt && in2.isInt)
      op0 match {
        case opII: BinaryOp.OpII =>
          stream.BinaryOp[Int    , BufI, Int   , BufI](op0.name, opII.funII, in1 = in1.toInt    , in2 = in2.toInt   ): StreamOut

        case opIL: BinaryOp.OpIL =>
          stream.BinaryOp[Int    , BufI, Long  , BufL](op0.name, opIL.funIL, in1 = in1.toInt    , in2 = in2.toInt   ): StreamOut

        case opID: BinaryOp.OpID =>
          stream.BinaryOp[Int    , BufI, Double, BufD](op0.name, opID.funID, in1 = in1.toInt    , in2 = in2.toInt   ): StreamOut

        case _ =>
          stream.BinaryOp[Double , BufD, Double, BufD](op0.name, op0 .funDD, in1 = in1.toDouble , in2 = in2.toDouble): StreamOut
      }
    }  
  }
}