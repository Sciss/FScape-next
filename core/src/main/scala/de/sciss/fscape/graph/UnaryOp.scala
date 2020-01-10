/*
 *  UnaryOp.scala
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
import de.sciss.fscape.stream.{StreamIn, StreamOut}
import de.sciss.numbers.{DoubleFunctions => rd, DoubleFunctions2 => rd2, IntFunctions => ri}

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}

object UnaryOp {
  object Op {
    def apply(id: Int): Op = (id: @switch) match {
      case Neg            .id => Neg
      case Not            .id => Not
      case Abs            .id => Abs
      case Ceil           .id => Ceil
      case Floor          .id => Floor
      case Frac           .id => Frac
      case Signum         .id => Signum
      case Squared        .id => Squared
      case Cubed          .id => Cubed
      case Sqrt           .id => Sqrt
      case Exp            .id => Exp
      case Reciprocal     .id => Reciprocal
      case Midicps        .id => Midicps
      case Cpsmidi        .id => Cpsmidi
      case Midiratio      .id => Midiratio
      case Ratiomidi      .id => Ratiomidi
      case Dbamp          .id => Dbamp
      case Ampdb          .id => Ampdb
      case Octcps         .id => Octcps
      case Cpsoct         .id => Cpsoct
      case Log            .id => Log
      case Log2           .id => Log2
      case Log10          .id => Log10
      case Sin            .id => Sin
      case Cos            .id => Cos
      case Tan            .id => Tan
      case Asin           .id => Asin
      case Acos           .id => Acos
      case Atan           .id => Atan
      case Sinh           .id => Sinh
      case Cosh           .id => Cosh
      case Tanh           .id => Tanh
      //      case Distort    .id => Distort
      //      case Softclip   .id => Softclip
      //      case Ramp       .id => Ramp
      //      case Scurve     .id => Scurve
      case IsNaN          .id => IsNaN
      case NextPowerOfTwo .id => NextPowerOfTwo
    }
  }

  sealed trait Op {
    op =>

    def id: Int

    def apply(a: Double): Double

    /** The default converts to `Double`, but specific operators
      * may better preserve semantics and precision for other types such as `Int` and `Long`.
      */
    def apply(a: Constant): Constant = ConstantD(apply(a.doubleValue))

    def name: String = plainName.capitalize

    final def make(a: GE): GE = a match {
      case v: Constant  => apply(v)
      case _            => UnaryOp(op.id, a)
    }

    private def plainName: String = {
      val cn = getClass.getName
      val sz = cn.length
      val i  = cn.indexOf('$') + 1
      cn.substring(i, if (cn.charAt(sz - 1) == '$') sz - 1 else sz)
    }
  }

  case object Neg extends Op {
    final val id = 0
    def apply(a: Double): Double = -a

    override def apply(a: Constant): Constant = a match {
      case ConstantD(d) => ConstantD(apply(d))
      case ConstantI(i) => ConstantI(-i)
      case ConstantL(n) => ConstantL(-n)
    }
  }

  case object Not extends Op {
    final val id = 1
    def apply(a: Double): Double = if (a == 0.0) 1.0 else 0.0

    override def apply(a: Constant): Constant = a match {
      case ConstantD(d) => ConstantD(apply(d))
      case ConstantI(i) => ConstantI(if (i == 0 ) 1  else 0 )
      case ConstantL(n) => ConstantL(if (n == 0L) 1L else 0L)
    }
  }

  // case object IsNil       extends Op(  2 )
  // case object NotNil      extends Op(  3 )
  // case object BitNot      extends Op(  4 )
  case object Abs extends Op {
    final val id = 5
    def apply(a: Double): Double = rd.abs(a)

    override def apply(a: Constant): Constant = a match {
      case ConstantD(d) => ConstantD(apply(d))
      case ConstantI(i) => ConstantI(math.abs(i))
      case ConstantL(n) => ConstantL(math.abs(n))
    }
  }

  // case object ToFloat     extends Op(  6 )
  // case object ToInt       extends Op(  7 )
  case object Ceil extends Op {
    final val id = 8
    def apply(a: Double): Double = rd.ceil(a)
  }

  case object Floor extends Op {
    final val id = 9
    def apply(a: Double): Double = rd.floor(a)
  }

  case object Frac extends Op {
    final val id = 10
    def apply(a: Double): Double = rd.frac(a)
  }

  case object Signum extends Op {
    final val id = 11
    def apply(a: Double): Double = math.signum(a)

    override def apply(a: Constant): Constant = a match {
      case ConstantD(d) => ConstantD(apply(d))
      case ConstantI(i) => ConstantI(math.signum(i))
      case ConstantL(n) => ConstantL(math.signum(n))
    }
  }

  private def mkIntOrLong(n: Long): Constant =
    if (n >= Int.MinValue && n <= Int.MaxValue)
      ConstantI(n.toInt)
    else
      ConstantL(n)

  case object Squared extends Op {
    final val id = 12
    def apply(a: Double): Double = rd.squared(a)

    override def apply(a: Constant): Constant = a match {
      case ConstantD(d) => ConstantD(apply(d))
      case ConstantI(i) =>
        val n = i.toLong * i.toLong
        if (n >= Int.MinValue && n <= Int.MaxValue)
          ConstantI(n.toInt)
        else
          ConstantL(n)
      case ConstantL(n) => ConstantL(n * n)
    }
  }

  case object Cubed extends Op {
    final val id = 13
    def apply(a: Double): Double = rd2.cubed(a)

    override def apply(a: Constant): Constant = a match {
      case ConstantD(d) => ConstantD(apply(d))
      case ConstantI(i) =>
        val n = i.toLong * i.toLong * i.toLong
        mkIntOrLong(n)
      case ConstantL(n) => ConstantL(n * n * n)
    }
  }

  case object Sqrt extends Op {
    final val id = 14
    def apply(a: Double): Double = rd.sqrt(a)
  }

  case object Exp extends Op {
    final val id = 15
    def apply(a: Double): Double = rd.exp(a)
  }

  case object Reciprocal extends Op {
    final val id = 16
    def apply(a: Double): Double = rd2.reciprocal(a)
  }

  case object Midicps extends Op {
    final val id = 17
    def apply(a: Double): Double = rd.midiCps(a)
  }

  case object Cpsmidi extends Op {
    final val id = 18
    def apply(a: Double): Double = rd.cpsMidi(a)
  }

  case object Midiratio extends Op {
    final val id = 19
    def apply(a: Double): Double = rd.midiRatio(a)
  }

  case object Ratiomidi extends Op {
    final val id = 20
    def apply(a: Double): Double = rd.ratioMidi(a)
  }

  case object Dbamp extends Op {
    final val id = 21
    def apply(a: Double): Double = rd.dbAmp(a)
  }

  case object Ampdb extends Op {
    final val id = 22
    def apply(a: Double): Double = rd.ampDb(a)
  }

  case object Octcps extends Op {
    final val id = 23
    def apply(a: Double): Double = rd.octCps(a)
  }

  case object Cpsoct extends Op {
    final val id = 24
    def apply(a: Double): Double = rd.cpsOct(a)
  }

  case object Log extends Op {
    final val id = 25
    def apply(a: Double): Double = rd.log(a)
  }

  case object Log2 extends Op {
    final val id = 26
    def apply(a: Double): Double = rd.log2(a)
  }

  case object Log10 extends Op {
    final val id = 27
    def apply(a: Double): Double = rd.log10(a)
  }

  case object Sin extends Op {
    final val id = 28
    def apply(a: Double): Double = rd.sin(a)
  }

  case object Cos extends Op {
    final val id = 29
    def apply(a: Double): Double = rd.cos(a)
  }

  case object Tan extends Op {
    final val id = 30
    def apply(a: Double): Double = rd.tan(a)
  }

  case object Asin extends Op {
    final val id = 31
    def apply(a: Double): Double = rd.asin(a)
  }

  case object Acos extends Op {
    final val id = 32
    def apply(a: Double): Double = rd.acos(a)
  }

  case object Atan extends Op {
    final val id = 33
    def apply(a: Double): Double = rd.atan(a)
  }

  case object Sinh extends Op {
    final val id = 34
    def apply(a: Double): Double = rd.sinh(a)
  }

  case object Cosh extends Op {
    final val id = 35
    def apply(a: Double): Double = rd.cosh(a)
  }

  case object Tanh extends Op {
    final val id = 36
    def apply(a: Double): Double = rd.tanh(a)
  }

  // class Rand              extends Op( 37 )
  // class Rand2             extends Op( 38 )
  // class Linrand           extends Op( 39 )
  // class Bilinrand         extends Op( 40 )
  // class Sum3rand          extends Op( 41 )

  //  case object Distort extends Op {
  //    final val id = 42
  //    def apply(a: Double): Double = rd2.distort(a)
  //  }
  //
  //  case object Softclip extends Op {
  //    final val id = 43
  //    def apply(a: Double): Double = rd2.softClip(a)
  //  }

  // class Coin              extends Op( 44 )
  // case object DigitValue  extends Op( 45 )
  // case object Silence     extends Op( 46 )
  // case object Thru        extends Op( 47 )
  // case object RectWindow  extends Op( 48 )
  // case object HanWindow   extends Op( 49 )
  // case object WelWindow   extends Op( 50 )
  // case object TriWindow   extends Op( 51 )

  //  case object Ramp extends Op {
  //    final val id = 52
  //    def apply(a: Double): Double = rd2.ramp(a)
  //  }
  //
  //  case object Scurve extends Op {
  //    final val id = 53
  //    def apply(a: Double): Double = rd2.sCurve(a)
  //  }

  case object IsNaN extends Op {
    final val id = 100
    def apply(a: Double): Double = if (java.lang.Double.isNaN(a)) 1.0 else 0.0
  }

  case object NextPowerOfTwo extends Op {
    final val id = 101
    def apply(a: Double): Double = ri.nextPowerOfTwo(Math.ceil(a).toInt).toDouble
  }
}
final case class UnaryOp(op: Int, in: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, inputs = args, adjuncts = Adjunct.Int(op) :: Nil)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in) = args
    val op0 = UnaryOp.Op(op)
    stream.UnaryOp(op = op0, in = in.toDouble)
  }
}