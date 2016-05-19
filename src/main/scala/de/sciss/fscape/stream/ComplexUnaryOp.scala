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

package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.scaladsl.GraphDSL
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import de.sciss.fscape.stream.impl.FilterIn1Impl

import scala.annotation.switch

/** Unary operator assuming stream is complex signal (real and imaginary interleaved).
  * Outputs another complex stream even if the operator yields a purely real-valued result
  * (ex. `abs`).
  *
  * XXX TODO - need more ops such as conjugate, polar-to-cartesian, ...
  */
object ComplexUnaryOp {
  def apply(op: Op, in: Outlet[BufD])
           (implicit builder: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] = {
    val stage0  = new Stage(op, ctrl)
    val stage   = builder.add(stage0)
    import GraphDSL.Implicits._
    in ~> stage.in
    stage.out
  }

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

  private final class Stage(op: Op, ctrl: Control) extends GraphStage[FlowShape[BufD, BufD]] {
    val shape = new FlowShape(
      in  = Inlet [BufD]("ComplexUnaryOp.in" ),
      out = Outlet[BufD]("ComplexUnaryOp.out")
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(op, shape, ctrl)
  }

  private final class Logic(op: Op,
                            protected val shape: FlowShape[BufD, BufD],
                            protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with FilterIn1Impl[BufD, BufD] {

    private[this] var inOff             = 0  // regarding `bufIn`
    private[this] var inRemain          = 0
    private[this] var outOff            = 0  // regarding `bufOut`
    private[this] var outRemain         = 0
    private[this] var outSent           = true

    @inline
    private[this] def allocOutBuf(): BufD = ctrl.borrowBufD()

    @inline
    private[this] def shouldRead = inRemain == 0 && canRead

    protected def process(): Unit = {
      var stateChange = false

      if (shouldRead) {
        readIns()
        inRemain    = bufIn.size
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

      val chunk = math.min(inRemain, outRemain) & ~1  // must be even
      if (chunk > 0) {
        op(in = bufIn.buf, inOff = inOff, out = bufOut.buf, outOff = outOff, len = chunk >> 1)
        inOff       += chunk
        inRemain    -= chunk
        outOff      += chunk
        outRemain   -= chunk
        stateChange  = true
      }

      val flushOut = inRemain <= 1 && isClosed(shape.in)  // flush also if inRemain == 1
      if (!outSent && (outRemain == 0 || flushOut) && isAvailable(shape.out)) {
        if (outOff > 0) {
          bufOut.size = outOff
          push(shape.out, bufOut)
        } else {
          bufOut.release()(ctrl)
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