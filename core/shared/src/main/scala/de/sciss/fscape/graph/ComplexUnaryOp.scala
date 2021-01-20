/*
 *  ComplexUnaryOp.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}

/** Unary operator assuming stream is complex signal (real and imaginary interleaved).
  * Outputs another complex stream even if the operator yields a purely real-valued result
  * (ex. `abs`).
  *
  * XXX TODO - need more ops such as conjugate, polar-to-cartesian, ...
  */
object ComplexUnaryOp extends ProductReader[ComplexUnaryOp] {
  object Op {
    def apply(id: Int): Op = (id: @switch) match {
      // case Neg        .id => Neg
      //      case Not        .id => Not
      case Abs        .id => Abs
      //      case Ceil       .id => Ceil
      //      case Floor      .id => Floor
      //      case Frac       .id => Frac
      //      case Signum     .id => Signum
      case Squared    .id => Squared
      case Cubed      .id => Cubed
      //      case Sqrt       .id => Sqrt
      case Exp        .id => Exp
      case Reciprocal .id => Reciprocal
      //      case Midicps    .id => Midicps
      //      case Cpsmidi    .id => Cpsmidi
      //      case Midiratio  .id => Midiratio
      //      case Ratiomidi  .id => Ratiomidi
      //      case Dbamp      .id => Dbamp
      //      case Ampdb      .id => Ampdb
      //      case Octcps     .id => Octcps
      //      case Cpsoct     .id => Cpsoct
      case Log        .id => Log
      case Log2       .id => Log2
      case Log10      .id => Log10
      //      case Sin        .id => Sin
      //      case Cos        .id => Cos
      //      case Tan        .id => Tan
      //      case Asin       .id => Asin
      //      case Acos       .id => Acos
      //      case Atan       .id => Atan
      //      case Sinh       .id => Sinh
      //      case Cosh       .id => Cosh
      //      case Tanh       .id => Tanh
      //      case Distort    .id => Distort
      //      case Softclip   .id => Softclip
      //      case Ramp       .id => Ramp
      //      case Scurve     .id => Scurve
      case Conj       .id => Conj
      case AbsSquared .id => AbsSquared
      case CarToPol   .id => CarToPol
      case PolToCar   .id => PolToCar
      case Real       .id => Real
      case Imag       .id => Imag
      case Mag        .id => Mag
      case Phase      .id => Phase
      case MagSquared .id => MagSquared
    }
  }

  sealed trait Op {
    op =>

    def id: Int

    /** If the operator outputs real values or complex values.
      * In the former case, `apply` advances `out` by `len`
      * frames, in the latter case, it advances by `len * 2` frames.
      */
    def realOutput: Boolean

    final def make(a: GE): GE = new ComplexUnaryOp(op.id, a)

    /** Transfers values from an input buffer
      * to an output buffer,
      * applying the operator.
      *
      * @param in       the buffer to read from, assuming interleaved re, im data
      * @param inOff    the index into `in`. this is a direct array index, not
      *                 a logical index which must be multiplied by two!
      * @param out      the buffer to read from, assuming interleaved re, im data
      * @param outOff   the index into `out`. this is a direct array index, not
      *                 a logical index which must be multiplied by two!
      * @param len      logical length of the operation, that is the number of
      *                 complex numbers to transfer. the number of `Double` values
      *                 read from `in` and written to `out` is twice `len`!
      */
    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit

    def name: String = plainName.capitalize

    private def plainName: String = {
      val cn = getClass.getName
      val sz = cn.length
      val i  = cn.indexOf('$') + 1
      cn.substring(i, if (cn.charAt(sz - 1) == '$') sz - 1 else sz)
    }
  }

  sealed trait ComplexOutput extends Op {
    final val realOutput = false
  }

  sealed trait RealOutput extends Op {
    final val realOutput = true
  }

  //  case object Neg extends Op {
  //    final val id = 0
  //    def apply(a: Double): Double = -a
  //  }

  //  case object Not extends Op {
  //    final val id = 1
  //    def apply(a: Double): Double = rd2.not(a)
  //  }

  // case object IsNil       extends Op(  2 )
  // case object NotNil      extends Op(  3 )
  // case object BitNot      extends Op(  4 )
  case object Abs extends ComplexOutput {
    final val id = 5

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      val inStop = inOff + (len << 1)
      var i = inOff
      var j = outOff
      while (i < inStop) {
        val inRe  = in(i); i += 1
        val inIm  = in(i); i += 1
        val outRe = math.sqrt(inRe * inRe + inIm * inIm)
        val outIm = 0.0
        out(j) = outRe; j += 1
        out(j) = outIm; j += 1
      }
    }
  }

  case object Squared extends ComplexOutput {
    final val id = 12

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      val inStop = inOff + (len << 1)
      var i = inOff
      var j = outOff
      while (i < inStop) {
        val inRe  = in(i); i += 1
        val inIm  = in(i); i += 1
        val outRe = inRe * inRe - inIm * inIm
        val outIm = inRe * inIm * 2
        out(j) = outRe; j += 1
        out(j) = outIm; j += 1
      }
    }
  }

  case object Cubed extends ComplexOutput {
    final val id = 13

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      val inStop = inOff + (len << 1)
      var i = inOff
      var j = outOff
      while (i < inStop) {
        val inRe  = in(i); i += 1
        val inIm  = in(i); i += 1
        val tmpRe = inRe * inRe - inIm * inIm
        val tmpIm = inRe * inIm * 2
        val outRe = tmpRe * inRe - tmpIm * inIm   // XXX TODO -- can we simplify this?
        val outIm = tmpRe * inIm + inRe  * tmpIm
        out(j) = outRe; j += 1
        out(j) = outIm; j += 1
      }
    }
  }

  case object Exp extends ComplexOutput {
    final val id = 15

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      // Mag  (out) = Exp(Re(in))
      // Phase(out) = Im(in)
      val inStop = inOff + (len << 1)
      var i = inOff
      var j = outOff
      while (i < inStop) {
        val inRe    = in(i); i += 1
        val inIm    = in(i); i += 1
        val outMag  = math.exp(inRe)
        val outPhase= inIm
        val outRe   = outMag * math.cos(outPhase)
        val outIm   = outMag * math.sin(outPhase)
        out(j) = outRe; j += 1
        out(j) = outIm; j += 1
      }
    }
  }

  case object Reciprocal extends ComplexOutput {
    final val id = 16

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      // Re(out) =  Re(in) / (Re(in)^2 + Im(in)^2)
      // Im(out) = -Im(in) / (Re(in)^2 + Im(in)^2)
      val inStop = inOff + (len << 1)
      var i = inOff
      var j = outOff
      while (i < inStop) {
        val inRe    = in(i); i += 1
        val inIm    = in(i); i += 1
        val div     = inRe * inRe + inIm * inIm
        val outRe   =  inRe / div
        val outIm   = -inIm / div
        out(j) = outRe; j += 1
        out(j) = outIm; j += 1
      }
    }
  }

  case object Log extends ComplexOutput {
    final val id = 25

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit =
      base(in = in, inOff = inOff, out = out, outOff = outOff, len = len, mul = 1.0)

    private[ComplexUnaryOp] def base(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int,
                                     mul: Double): Unit = {
      // Re(out) = Log(Mag(in))
      // Im(out) = Phase(in)
      val inStop = inOff + (len << 1)
      var i = inOff
      var j = outOff
      while (i < inStop) {
        val inRe    = in(i); i += 1
        val inIm    = in(i); i += 1
        val inMag   = math.sqrt(inRe * inRe + inIm * inIm)
        val inPhase = math.atan2(inIm, inRe)
        val outRe   = math.log(inMag) * mul
        val outIm   = inPhase         * mul
        out(j) = outRe; j += 1
        out(j) = outIm; j += 1
      }
    }
  }

  case object Log2 extends ComplexOutput {
    final val id = 26

    private[this] final val Ln2R = 1.0 / math.log(2)

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit =
      Log.base(in = in, inOff = inOff, out = out, outOff = outOff, len = len, mul = Ln2R)
  }

  case object Log10 extends ComplexOutput {
    final val id = 27

    private[this] final val Ln10R = 1.0 / math.log(10)

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit =
      Log.base(in = in, inOff = inOff, out = out, outOff = outOff, len = len, mul = Ln10R)
  }

  case object Conj extends ComplexOutput {
    final val id = 100

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      val inStop = inOff + (len << 1)
      var i = inOff
      var j = outOff
      while (i < inStop) {
        val inRe    = in(i); i += 1
        val inIm    = in(i); i += 1
        val outRe   = inRe
        val outIm   = -inIm
        out(j) = outRe; j += 1
        out(j) = outIm; j += 1
      }
    }
  }

  case object AbsSquared extends ComplexOutput {
    final val id = 101

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      val inStop = inOff + (len << 1)
      var i = inOff
      var j = outOff
      while (i < inStop) {
        val inRe  = in(i); i += 1
        val inIm  = in(i); i += 1
        val outRe = inRe * inRe + inIm * inIm
        val outIm = 0.0
        out(j) = outRe; j += 1
        out(j) = outIm; j += 1
      }
    }
  }

  case object CarToPol extends ComplexOutput {
    final val id = 102

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      val inStop = inOff + (len << 1)
      var i = inOff
      var j = outOff
      while (i < inStop) {
        val inRe    = in(i); i += 1
        val inIm    = in(i); i += 1
        val mag     = math.sqrt(inRe * inRe + inIm * inIm)
        val phase   = math.atan2(inIm, inRe)
        out(j) = mag  ; j += 1
        out(j) = phase; j += 1
      }
    }
  }

  case object PolToCar extends ComplexOutput {
    final val id = 103

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      val inStop = inOff + (len << 1)
      var i = inOff
      var j = outOff
      while (i < inStop) {
        val inMag   = in(i); i += 1
        val inPhase = in(i); i += 1
        val outRe   = math.cos(inPhase) * inMag
        val outIm   = math.sin(inPhase) * inMag
        out(j) = outRe; j += 1
        out(j) = outIm; j += 1
      }
    }
  }

  case object Real extends RealOutput {
    final val id = 104

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      val outStop = outOff + len
      var i = inOff
      var j = outOff
      while (j < outStop) {
        out(j) = in(i)
        i += 2
        j += 1
      }
    }
  }

  case object Imag extends RealOutput {
    final val id = 105

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      val outStop = outOff + len
      var i = inOff + 1
      var j = outOff
      while (j < outStop) {
        out(j) = in(i)
        i += 2
        j += 1
      }
    }
  }

  case object Mag extends RealOutput {
    final val id = 106

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      val outStop = outOff + len
      var i = inOff
      var j = outOff
      while (j < outStop) {
        val inRe    = in(i); i += 1
        val inIm    = in(i); i += 1
        val mag     = math.sqrt(inRe * inRe + inIm * inIm)
        out(j) = mag; j += 1
      }
    }
  }

  case object Phase extends RealOutput {
    final val id = 107

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      val outStop = outOff + len
      var i = inOff
      var j = outOff
      while (j < outStop) {
        val inRe    = in(i); i += 1
        val inIm    = in(i); i += 1
        val phase   = math.atan2(inIm, inRe)
        out(j) = phase; j += 1
      }
    }
  }

  case object MagSquared extends RealOutput {
    final val id = 108

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      val outStop = outOff + len
      var i = inOff
      var j = outOff
      while (j < outStop) {
        val inRe    = in(i); i += 1
        val inIm    = in(i); i += 1
        val magSq   = inRe * inRe + inIm * inIm
        out(j) = magSq; j += 1
      }
    }
  }

  override def read(in: RefMapIn, key: String, arity: Int): ComplexUnaryOp = {
    require (arity == 2)
    val _op = in.readInt()
    val _in = in.readGE()
    new ComplexUnaryOp(_op, _in)
  }
}
final case class ComplexUnaryOp(op: Int, in: GE) extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, inputs = args, adjuncts = Adjunct.Int(op) :: Nil)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in) = args
    val op0 = ComplexUnaryOp.Op(op)
    stream.ComplexUnaryOp(op = op0, in = in.toDouble)
  }
}