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
import de.sciss.fscape.stream.{BufD, BufI, BufL, StreamIn, StreamOut}
import de.sciss.numbers.{DoubleFunctions => rd, DoubleFunctions2 => rd2, IntFunctions => ri, IntFunctions2 => ri2, LongFunctions => rl, LongFunctions2 => rl2}

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}

object UnaryOp {
  object Op {
    def apply(id: Int): Op = (id: @switch) match {
      case Neg            .id => Neg
      case Not            .id => Not
      case BitNot         .id => BitNot
      case Abs            .id => Abs
      case ToDouble       .id => ToDouble
      case ToInt          .id => ToInt
      case Ceil           .id => Ceil
      case Floor          .id => Floor
      case Frac           .id => Frac
      case Signum         .id => Signum
      case Squared        .id => Squared
      case Cubed          .id => Cubed
      case Sqrt           .id => Sqrt
      case Exp            .id => Exp
      case Reciprocal     .id => Reciprocal
      case MidiCps        .id => MidiCps
      case CpsMidi        .id => CpsMidi
      case MidiRatio      .id => MidiRatio
      case RatioMidi      .id => RatioMidi
      case DbAmp          .id => DbAmp
      case AmpDb          .id => AmpDb
      case OctCps         .id => OctCps
      case CpsOct         .id => CpsOct
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
      case ToLong         .id => ToLong
    }
  }

  sealed trait Op {
    op =>

    def id: Int

    def funDD: Double => Double

//    def apply(a: Double): Double

    /** The default converts to `Double`, but specific operators
      * may better preserve semantics and precision for other types such as `Int` and `Long`.
      */
    def apply(a: Constant): Constant = ConstantD(funDD(a.doubleValue))

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

  sealed trait OpII extends Op {
    def funII: Int => Int

    override def apply(a: Constant): Constant = a match {
      case ConstantI(i) => ConstantI(funII(i))
      case _            => ConstantD(funDD(a.doubleValue))
    }
  }

  sealed trait OpLL extends Op {
    def funLL: Long => Long

    override def apply(a: Constant): Constant = a match {
      case ConstantL(n) => ConstantL(funLL(n))
      case _            => ConstantD(funDD(a.doubleValue))
    }
  }

  sealed trait OpDI extends Op {
    def funDI: Double => Int
  }

  sealed trait OpDL extends Op {
    def funDL: Double => Long
  }

  sealed trait OpID extends Op {
    def funID: Int => Double
  }

  sealed trait OpLD extends Op {
    def funLD: Long => Double
  }

  sealed trait OpIL extends Op {
    def funIL: Int => Long
  }

  sealed trait OpLI extends Op {
    def funLI: Long => Int
  }

  sealed trait OpSame extends Op with OpII with OpLL {
    override def apply(a: Constant): Constant = a match {
      case ConstantD(d) => ConstantD(funDD(d))
      case ConstantI(i) => ConstantI(funII(i))
      case ConstantL(n) => ConstantL(funLL(n))
    }
  }

  case object Neg extends OpSame {
    final val id = 0

    val funDD: Double => Double = -_
    val funII: Int    => Int    = -_
    val funLL: Long   => Long   = -_
  }

  case object Not extends OpII {
    final val id = 1

    val funDD: Double => Double = a => if (a == 0.0 ) 1.0 else 0.0
    val funII: Int    => Int    = a => if (a == 0   ) 1   else 0
  }

  // case object IsNil       extends Op(  2 )
  // case object NotNil      extends Op(  3 )

  case object BitNot extends OpSame {
    final val id = 4

    val funDD: Double => Double = a => (~a.toLong).toDouble
    val funII: Int    => Int    = a => ~a
    val funLL: Long   => Long   = a => ~a
  }

  case object Abs extends OpSame {
    final val id = 5

    val funDD: Double => Double = a => rd.abs(a)
    val funII: Int    => Int    = a => ri.abs(a)
    val funLL: Long   => Long   = a => rl.abs(a)
  }

  case object ToDouble extends OpID with OpLD {
    final val id = 6

    val funDD: Double => Double = a => a
    val funID: Int    => Double = a => a.toDouble
    val funLD: Long   => Double = a => a.toDouble

    override def apply(a: Constant): Constant = a match {
      case ConstantD(d) => ConstantD(funDD(d))
      case ConstantI(i) => ConstantD(funID(i))
      case ConstantL(n) => ConstantD(funLD(n))
    }
  }

  case object ToInt extends OpDI with OpLI with OpII {
    final val id = 7

    val funDD: Double => Double = a => a.toInt.toDouble

    val funDI: Double => Int    = a => a.toInt
    val funLI: Long   => Int    = a => a.toInt
    val funII: Int    => Int    = a => a

    override def apply(a: Constant): Constant = a match {
      case ConstantD(d) => ConstantI(funDI(d))
      case ConstantI(i) => ConstantI(funII(i))
      case ConstantL(n) => ConstantI(funLI(n))
    }
  }

  case object Ceil extends Op {
    final val id = 8

    val funDD: Double => Double = a => rd.ceil(a)
  }

  case object Floor extends Op {
    final val id = 9

    val funDD: Double => Double = a => rd.floor(a)
  }

  case object Frac extends Op {
    final val id = 10

    val funDD: Double => Double = a => rd.frac(a)
  }

  case object Signum extends OpSame {
    final val id = 11

    val funDD: Double => Double = a => rd.signum(a)
    val funII: Int    => Int    = a => ri.signum(a)
    val funLL: Long   => Long   = a => rl.signum(a)
  }

  case object Squared extends Op with OpIL with OpLL {
    final val id = 12

    val funDD: Double => Double = a => rd.squared(a)
    val funIL: Int    => Long   = a => ri.squared(a)
    val funLL: Long   => Long   = a => rl.squared(a)

    override def apply(a: Constant): Constant = a match {
      case ConstantD(d) => ConstantD(funDD(d))
      case ConstantI(i) => ConstantL(funIL(i))
      case ConstantL(n) => ConstantL(funLL(n))
    }
  }

  case object Cubed extends Op with OpIL with OpLL {
    final val id = 13

    val funDD: Double => Double = a => rd2.cubed(a)
    val funIL: Int    => Long   = a => ri2.cubed(a)
    val funLL: Long   => Long   = a => rl2.cubed(a)

    override def apply(a: Constant): Constant = a match {
      case ConstantD(d) => ConstantD(funDD(d))
      case ConstantI(i) => ConstantL(funIL(i))
      case ConstantL(n) => ConstantL(funLL(n))
    }
  }

  case object Sqrt extends Op {
    final val id = 14

    val funDD: Double => Double = a => rd.sqrt(a)
  }

  case object Exp extends Op {
    final val id = 15

    val funDD: Double => Double = a => rd.exp(a)
  }

  case object Reciprocal extends Op with OpID with OpLD {
    final val id = 16

    val funDD: Double => Double = a => rd2.reciprocal(a)
    val funID: Int    => Double = a => rd2.reciprocal(a.toDouble)
    val funLD: Long   => Double = a => rd2.reciprocal(a.toDouble)
  }

  case object MidiCps extends Op {
    final val id = 17

    val funDD: Double => Double = a => rd.midiCps(a)
  }

  case object CpsMidi extends Op {
    final val id = 18

    val funDD: Double => Double = a => rd.cpsMidi(a)
  }

  case object MidiRatio extends Op {
    final val id = 19

    val funDD: Double => Double = a => rd.midiRatio(a)
  }

  case object RatioMidi extends Op {
    final val id = 20

    val funDD: Double => Double = a => rd.ratioMidi(a)
  }

  case object DbAmp extends Op {
    final val id = 21

    val funDD: Double => Double = a => rd.dbAmp(a)
  }

  case object AmpDb extends Op {
    final val id = 22

    val funDD: Double => Double = a => rd.ampDb(a)
  }

  case object OctCps extends Op {
    final val id = 23

    val funDD: Double => Double = a => rd.octCps(a)
  }

  case object CpsOct extends Op {
    final val id = 24

    val funDD: Double => Double = a => rd.cpsOct(a)
  }

  case object Log extends Op {
    final val id = 25

    val funDD: Double => Double = a => rd.log(a)
  }

  case object Log2 extends Op {
    final val id = 26

    val funDD: Double => Double = a => rd.log2(a)
  }

  case object Log10 extends Op {
    final val id = 27

    val funDD: Double => Double = a => rd.log10(a)
  }

  case object Sin extends Op {
    final val id = 28

    val funDD: Double => Double = a => rd.sin(a)
  }

  case object Cos extends Op {
    final val id = 29

    val funDD: Double => Double = a => rd.cos(a)
  }

  case object Tan extends Op {
    final val id = 30

    val funDD: Double => Double = a => rd.tan(a)
  }

  case object Asin extends Op {
    final val id = 31

    val funDD: Double => Double = a => rd.asin(a)
  }

  case object Acos extends Op {
    final val id = 32

    val funDD: Double => Double = a => rd.acos(a)
  }

  case object Atan extends Op {
    final val id = 33

    val funDD: Double => Double = a => rd.atan(a)
  }

  case object Sinh extends Op {
    final val id = 34

    val funDD: Double => Double = a => rd.sinh(a)
  }

  case object Cosh extends Op {
    final val id = 35

    val funDD: Double => Double = a => rd.cosh(a)
  }

  case object Tanh extends Op {
    final val id = 36

    val funDD: Double => Double = a => rd.tanh(a)
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

  case object IsNaN extends Op with OpDI {
    final val id = 100

    val funDD: Double => Double = a => if (java.lang.Double.isNaN(a)) 1.0 else 0.0
    val funDI: Double => Int    = a => if (java.lang.Double.isNaN(a)) 1   else 0

    override def apply(a: Constant): Constant =
      ConstantI(funDI(a.doubleValue))
  }

  case object NextPowerOfTwo extends OpSame {
    final val id = 101

    val funDD: Double => Double = a => ri.nextPowerOfTwo(Math.ceil(a).toInt).toDouble
    val funII: Int    => Int    = a => ri.nextPowerOfTwo(a)
    val funLL: Long   => Long   = a => rl.nextPowerOfTwo(a)
  }

  case object ToLong extends OpDL with OpLL with OpIL {
    final val id = 200

    val funDD: Double => Double = a => a.toInt.toDouble

    val funDL: Double => Long   = a => a.toLong
    val funLL: Long   => Long   = a => a
    val funIL: Int    => Long   = a => a.toLong

    override def apply(a: Constant): Constant = a match {
      case ConstantD(d) => ConstantL(funDL(d))
      case ConstantI(i) => ConstantL(funIL(i))
      case ConstantL(n) => ConstantL(funLL(n))
    }
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

    if (in.isDouble) {
      op0 match {
        case opDI: UnaryOp.OpDI =>
          stream.UnaryOp[Double , BufD, Int   , BufI](op0.name, opDI.funDI, in = in.toDouble): StreamOut

        case opDL: UnaryOp.OpDL =>
          stream.UnaryOp[Double , BufD, Long  , BufL](op0.name, opDL.funDL, in = in.toDouble): StreamOut

        case _ =>
          stream.UnaryOp[Double , BufD, Double, BufD](op0.name, op0 .funDD, in = in.toDouble): StreamOut
      }
    } else if (in.isInt) {
      op0 match {
        case opII: UnaryOp.OpII =>
          stream.UnaryOp[Int    , BufI, Int   , BufI](op0.name, opII.funII, in = in.toInt   ): StreamOut

        case opIL: UnaryOp.OpIL =>
          stream.UnaryOp[Int    , BufI, Long  , BufL](op0.name, opIL.funIL, in = in.toInt   ): StreamOut

        case opID: UnaryOp.OpID =>
          stream.UnaryOp[Int    , BufI, Double, BufD](op0.name, opID.funID, in = in.toInt   ): StreamOut

        case _ =>
          stream.UnaryOp[Double , BufD, Double, BufD](op0.name, op0 .funDD, in = in.toDouble): StreamOut
      }
      
    } else /*if (in.isLong)*/ {
      op0 match {
        case opLI: UnaryOp.OpLI =>
          stream.UnaryOp[Long   , BufL, Int   , BufI](op0.name, opLI.funLI, in = in.toLong  ): StreamOut

        case opLL: UnaryOp.OpLL =>
          stream.UnaryOp[Long   , BufL, Long  , BufL](op0.name, opLL.funLL, in = in.toLong  ): StreamOut

        case opLD: UnaryOp.OpLD =>
          stream.UnaryOp[Long   , BufL, Double, BufD](op0.name, opLD.funLD, in = in.toLong  ): StreamOut

        case _ =>
          stream.UnaryOp[Double , BufD, Double, BufD](op0.name, op0 .funDD, in = in.toDouble): StreamOut
      }
    }
  }
}