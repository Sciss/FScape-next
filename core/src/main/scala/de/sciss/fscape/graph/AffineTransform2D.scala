/*
 *  AffineTransform2D.scala
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

import scala.collection.immutable.{IndexedSeq => Vec}

/** An affine transformation UGen for image rotation, scaling, translation, shearing.
  * It uses a band-limited resampling algorithm.
  *
  * All window defining parameters (`widthIn`, `heightIn`, `widthOut`, `heightOut`)
  * are polled once per matrix. All matrix and filter parameters are polled one per
  * output pixel.
  *
  * @param in             the signal to resample
  * @param widthIn        the width (number of columns) of the input matrix
  * @param heightIn       the height (number of rows) of the input matrix
  * @param widthOut       the width (number of columns) of the output matrix
  * @param heightOut      the height (number of rows) of the output matrix
  * @param m00            coefficient of the first column of the first row (scale-x)
  * @param m10            coefficient of the first column of the second row (shear-y)
  * @param m01            coefficient of the second column of the first row (shear-x)
  * @param m11            coefficient of the second column of the second row (scale-y)
  * @param m02            coefficient of the third column of the first row (translate-x)
  * @param m12            coefficient of the third column of the second row (translate-y)
  * @param wrap           if non-zero, wraps coordinates around the input images boundaries.
  * @param rollOff        the FIR anti-aliasing roll-off width
  * @param kaiserBeta     the FIR windowing function's parameter
  * @param zeroCrossings  the number of zero-crossings in the truncated and windowed sinc FIR.
  */
final case class AffineTransform2D(in: GE, widthIn: GE, heightIn: GE, widthOut: GE, heightOut: GE,
                                   m00: GE, m10: GE, m01: GE, m11: GE, m02: GE, m12: GE, wrap: GE = 0,
                                   rollOff: GE = 0.86, kaiserBeta: GE = 7.5, zeroCrossings: GE = 15)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(Vector(in.expand, widthIn.expand, heightIn.expand, widthOut.expand, heightOut.expand,
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