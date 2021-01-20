/*
 *  AffineTransform2D.scala
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
import de.sciss.fscape.Ops._
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object AffineTransform2D extends ProductReader[AffineTransform2D] {
  def scale(in: GE, widthIn: GE, heightIn: GE, widthOut: GE = 0, heightOut: GE  = 0,
            sx: GE, sy: GE,
            wrap: GE = 1, rollOff: GE = 0.86, kaiserBeta: GE = 7.5, zeroCrossings: GE = 15): AffineTransform2D = {
    val m00 = sx
    val m10 = 0.0
    val m01 = 0.0
    val m11 = sy
    val m02 = 0.0
    val m12 = 0.0
    apply(in = in, widthIn = widthIn, heightIn = heightIn, widthOut = widthOut, heightOut = heightOut,
      m00 = m00, m10 = m10, m01 = m01, m11 = m11, m02 = m02, m12 = m12,
      wrap = wrap, rollOff = rollOff, kaiserBeta = kaiserBeta, zeroCrossings = zeroCrossings)
  }

  def translate(in: GE, widthIn: GE, heightIn: GE, widthOut: GE = 0, heightOut: GE = 0,
                tx: GE, ty: GE,
                wrap: GE = 1, rollOff: GE = 0.86, kaiserBeta: GE = 7.5, zeroCrossings: GE = 15): AffineTransform2D = {
    val m00 = 1.0
    val m10 = 0.0
    val m01 = 0.0
    val m11 = 1.0
    val m02 = tx
    val m12 = ty
    apply(in = in, widthIn = widthIn, heightIn = heightIn, widthOut = widthOut, heightOut = heightOut,
      m00 = m00, m10 = m10, m01 = m01, m11 = m11, m02 = m02, m12 = m12,
      wrap = wrap, rollOff = rollOff, kaiserBeta = kaiserBeta, zeroCrossings = zeroCrossings)
  }

  def rotate(in: GE, widthIn: GE, heightIn: GE, widthOut: GE = 0, heightOut: GE = 0,
             theta: GE, ax: GE = 0, ay: GE = 0,
             wrap: GE = 1, rollOff: GE = 0.86, kaiserBeta: GE = 7.5, zeroCrossings: GE = 15): AffineTransform2D = {
    val sin   = theta.sin
    val cos   = theta.cos
    val m00   = cos
    val m10   = sin
    val m01   = -sin
    val m11   = cos
    val mCos  = 1.0 - cos
    val m02   = ax * mCos + ay * sin
    val m12   = ay * mCos - ax * sin

    apply(in = in, widthIn = widthIn, heightIn = heightIn, widthOut = widthOut, heightOut = heightOut,
      m00 = m00, m10 = m10, m01 = m01, m11 = m11, m02 = m02, m12 = m12,
      wrap = wrap, rollOff = rollOff, kaiserBeta = kaiserBeta, zeroCrossings = zeroCrossings)
  }

  def shear(in: GE, widthIn: GE, heightIn: GE, widthOut: GE = 0, heightOut: GE = 0,
            shx: GE, shy: GE,
            wrap: GE = 1, rollOff: GE = 0.86, kaiserBeta: GE = 7.5, zeroCrossings: GE = 15): AffineTransform2D = {
    val m00 = 1.0
    val m01 = shx
    val m10 = shy
    val m11 = 1.0
    val m02 = 0.0
    val m12 = 0.0
    apply(in = in, widthIn = widthIn, heightIn = heightIn, widthOut = widthOut, heightOut = heightOut,
      m00 = m00, m10 = m10, m01 = m01, m11 = m11, m02 = m02, m12 = m12,
      wrap = wrap, rollOff = rollOff, kaiserBeta = kaiserBeta, zeroCrossings = zeroCrossings)
  }

  override def read(in: RefMapIn, key: String, arity: Int): AffineTransform2D = {
    require (arity == 15)
    val _in             = in.readGE()
    val _widthIn        = in.readGE()
    val _heightIn       = in.readGE()
    val _widthOut       = in.readGE()
    val _heightOut      = in.readGE()
    val _m00            = in.readGE()
    val _m10            = in.readGE()
    val _m01            = in.readGE()
    val _m11            = in.readGE()
    val _m02            = in.readGE()
    val _m12            = in.readGE()
    val _wrap           = in.readGE()
    val _rollOff        = in.readGE()
    val _kaiserBeta     = in.readGE()
    val _zeroCrossings  = in.readGE()
    new AffineTransform2D(
      _in,
      _widthIn,
      _heightIn,
      _widthOut,
      _heightOut,
      _m00,
      _m10,
      _m01,
      _m11,
      _m02,
      _m12,
      _wrap,
      _rollOff,
      _kaiserBeta,
      _zeroCrossings,
    )
  }
}

/** An affine transformation UGen for image rotation, scaling, translation, shearing.
  * It uses either a sinc-based band-limited resampling algorithm, or
  * bicubic interpolation, depending on the `zeroCrossings` parameter.
  *
  * All window defining parameters (`widthIn`, `heightIn`, `widthOut`, `heightOut`)
  * are polled once per matrix. All matrix and filter parameters are polled one per
  * output pixel.
  *
  * @param in             the signal to resample
  * @param widthIn        the width (number of columns) of the input matrix
  * @param heightIn       the height (number of rows) of the input matrix
  * @param widthOut       the width (number of columns) of the output matrix.
  *                       the special value zero (default) means it is the same as `widthIn`.
  * @param heightOut      the height (number of rows) of the output matrix.
  *                       the special value zero (default) means it is the same as `heightIn`.
  * @param m00            coefficient of the first column of the first row (scale-x)
  * @param m10            coefficient of the first column of the second row (shear-y)
  * @param m01            coefficient of the second column of the first row (shear-x)
  * @param m11            coefficient of the second column of the second row (scale-y)
  * @param m02            coefficient of the third column of the first row (translate-x)
  * @param m12            coefficient of the third column of the second row (translate-y)
  * @param wrap           if non-zero, wraps coordinates around the input images boundaries.
  *                       __TODO:__ currently `wrap = 0` is broken if using sinc interpolation!
  * @param rollOff        the FIR anti-aliasing roll-off width. Between zero and one.
  * @param kaiserBeta     the FIR windowing function's parameter
  * @param zeroCrossings  the number of zero-crossings in the truncated and windowed sinc FIR.
  *                       If zero, algorithm uses bicubic interpolation instead.
  *
  * @see [[ScanImage]]
  */
final case class AffineTransform2D(in           : GE,
                                   widthIn      : GE,
                                   heightIn     : GE,
                                   widthOut     : GE = 0,
                                   heightOut    : GE = 0,
                                   m00          : GE,
                                   m10          : GE,
                                   m01          : GE,
                                   m11          : GE,
                                   m02          : GE,
                                   m12          : GE,
                                   wrap         : GE = 1,
                                   rollOff      : GE = 0.86,
                                   kaiserBeta   : GE = 7.5,
                                   zeroCrossings: GE = 15,
                                  )
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, widthIn.expand, heightIn.expand, widthOut.expand, heightOut.expand,
      m00.expand, m10.expand, m01.expand, m11.expand, m02.expand, m12.expand, wrap.expand,
      rollOff.expand, kaiserBeta.expand, zeroCrossings.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, widthIn, heightIn, widthOut, heightOut, m00, m10, m01, m11, m02, m12, wrap,
            rollOff, kaiserBeta, zeroCrossings) = args
    stream.AffineTransform2D(in = in.toDouble, widthIn = widthIn.toInt, heightIn = heightIn.toInt,
      widthOut = widthOut.toInt, heightOut = heightOut.toInt,
      m00 = m00.toDouble, m10 = m10.toDouble, m01 = m01.toDouble, m11 = m11.toDouble,
      m02 = m02.toDouble, m12 = m12.toDouble, wrap = wrap.toInt,
      rollOff = rollOff.toDouble, kaiserBeta = kaiserBeta.toDouble, zeroCrossings = zeroCrossings.toInt)
  }
}