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
import de.sciss.numbers.{DoubleFunctions => rd, DoubleFunctions2 => rd2}

import scala.annotation.switch

/** Unary operator assuming stream is complex signal (real and imaginary interleaved).
  * Outputs another complex stream even if the operator yields a purely real-valued result
  * (ex. `abs`).
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
      case Sqrt       .id => Sqrt
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
        val outRe = ??? : Double // inRe * inRe - inIm * inIm
        val outIm = ??? : Double // inRe * inIm * 2
        out(j) = outRe; j += 1
        out(j) = outIm; j += 1
      }
    }
  }

  case object Sqrt extends Op {
    final val id = 14

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      ???
    }
  }

  case object Exp extends Op {
    final val id = 15

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      ???
    }
  }

  case object Reciprocal extends Op {
    final val id = 16

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      ???
    }
  }

  case object Log extends Op {
    final val id = 25

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      ???
    }
  }

  case object Log2 extends Op {
    final val id = 26

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      ???
    }
  }

  case object Log10 extends Op {
    final val id = 27

    def apply(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      ???
    }
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

      val chunk = math.min(inRemain, outRemain)
      if (chunk > 0) {
        var inOffI  = inOff
        var outOffI = outOff
        val inStop  = inOffI + chunk
        val in      = bufIn .buf
        val out     = bufOut.buf
        while (inOffI < inStop) {
          ??? // out(outOffI) = op(in(inOffI))
          inOffI  += 1
          outOffI += 1
        }
        inOff        = inOffI
        inRemain    -= chunk
        outOff       = outOffI
        outRemain   -= chunk
        stateChange  = true
      }

      val flushOut = inRemain == 0 && isClosed(shape.in)
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