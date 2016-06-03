/*
 *  ComplexUnaryOp.scala
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

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}

/** Unary operator assuming stream is complex signal (real and imaginary interleaved).
  * Outputs another complex stream even if the operator yields a purely real-valued result
  * (ex. `abs`).
  *
  * XXX TODO - need more ops such as conjugate, polar-to-cartesian, ...
  */
object ComplexUnaryOp {
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
    }
  }

  sealed trait Op {
    op =>

    def id: Int

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
  case object Abs extends Op {
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

  case object Squared extends Op {
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

  case object Cubed extends Op {
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

  case object Exp extends Op {
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

  case object Reciprocal extends Op {
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

  case object Log extends Op {
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

  case object Log2 extends Op {
    final val id = 26

    private[this] final val Ln2R = 1.0 / math.log(2)

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit =
      Log.base(in = in, inOff = inOff, out = out, outOff = outOff, len = len, mul = Ln2R)
  }

  case object Log10 extends Op {
    final val id = 27

    private[this] final val Ln10R = 1.0 / math.log(10)

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit =
      Log.base(in = in, inOff = inOff, out = out, outOff = outOff, len = len, mul = Ln10R)
  }

  case object Conj extends Op {
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
}
final case class ComplexUnaryOp(op: ComplexUnaryOp.Op, in: GE) extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(Vector(in.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamIn = ???
}