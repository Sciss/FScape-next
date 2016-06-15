/*
 *  ComplexBinaryOp.scala
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

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}

/** Binary operator assuming operands are complex signals (real and imaginary interleaved).
  * Outputs another complex stream even if the operator yields a purely real-valued result.
  *
  * XXX TODO - need more ops such as conjugate, polar-to-cartesian, ...
  */
object ComplexBinaryOp {
  object Op {
    def apply(id: Int): Op = (id: @switch) match {
      case Plus     .id => Plus
      case Minus    .id => Minus
      case Times    .id => Times
      // case Div      .id => Div
      // case Mod      .id => Mod
//      case Eq       .id => Eq
//      case Neq      .id => Neq
//      case Difsqr   .id => Difsqr
//      case Sumsqr   .id => Sumsqr
//      case Sqrsum   .id => Sqrsum
//      case Sqrdif   .id => Sqrdif
//      case Absdif   .id => Absdif
    }
  }

  sealed trait Op {
    op =>

    def id: Int

    final def make(a: GE, b: GE): GE = new ComplexBinaryOp(op, a = a, b = b)

    /** Transfers values from an input buffer
      * to an output buffer,
      * applying the operator.
      *
      * @param a        the buffer of the first operand to read from, assuming interleaved re, im data
      * @param b        the buffer of the second operand to read from, assuming interleaved re, im data
      * @param aOff     the index into `a`. this is a direct array index, not
      *                 a logical index which must be multiplied by two!
      * @param bOff     the index into `b`.
      * @param out      the buffer to read from, assuming interleaved re, im data
      * @param outOff   the index into `out`. this is a direct array index, not
      *                 a logical index which must be multiplied by two!
      * @param len      logical length of the operation, that is the number of
      *                 complex numbers to transfer. the number of `Double` values
      *                 read from `in` and written to `out` is twice `len`!
      */
    def apply(a: Array[Double], aOff: Int, b: Array[Double], bOff: Int, out: Array[Double], outOff: Int, len: Int): Unit

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

    def apply(a: Array[Double], aOff: Int, b: Array[Double], bOff: Int, out: Array[Double],
              outOff: Int, len: Int): Unit = {
      val aStop = aOff + (len << 1)
      var i     = aOff
      var j     = bOff
      var k     = outOff
      while (i < aStop) {
        val aRe   = a(i); i += 1
        val aIm   = a(i); i += 1
        val bRe   = b(j); j += 1
        val bIm   = b(j); j += 1
        val outRe = aRe + bRe
        val outIm = aIm + bIm
        out(k) = outRe; k += 1
        out(k) = outIm; k += 1
      }
    }
  }

  case object Minus extends Op {
    final val id = 1
    override val name = "-"

    def apply(a: Array[Double], aOff: Int, b: Array[Double], bOff: Int, out: Array[Double],
              outOff: Int, len: Int): Unit = {
      val aStop = aOff + (len << 1)
      var i     = aOff
      var j     = bOff
      var k     = outOff
      while (i < aStop) {
        val aRe   = a(i); i += 1
        val aIm   = a(i); i += 1
        val bRe   = b(j); j += 1
        val bIm   = b(j); j += 1
        val outRe = aRe - bRe
        val outIm = aIm - bIm
        out(k) = outRe; k += 1
        out(k) = outIm; k += 1
      }
    }
  }

  case object Times extends Op {
    final val id = 2
    override val name = "*"

    def apply(a: Array[Double], aOff: Int, b: Array[Double], bOff: Int, out: Array[Double], 
              outOff: Int, len: Int): Unit = {
      val aStop = aOff + (len << 1)
      var i     = aOff
      var j     = bOff
      var k     = outOff
      while (i < aStop) {
        val aRe   = a(i); i += 1
        val aIm   = a(i); i += 1
        val bRe   = b(j); j += 1
        val bIm   = b(j); j += 1
        val outRe = aRe - bRe
        val outIm = aIm - bIm
        out(k) = outRe; k += 1
        out(k) = outIm; k += 1
      }
    }
  }
}
final case class ComplexBinaryOp(op: ComplexBinaryOp.Op, a: GE, b: GE) extends UGenSource.SingleOut {

  protected def makeUGens(implicit builder: UGenGraph.Builder): UGenInLike =
    unwrap(Vector(a.expand, b.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit builder: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, inputs = args, rest = op)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit builder: stream.Builder): StreamOut = {
    val Vec(a, b) = args
    stream.ComplexBinaryOp(op = op, a = a.toDouble, b = b.toDouble)
  }
}