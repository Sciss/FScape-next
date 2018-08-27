/*
 *  ScanImage.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen that scans the pixels of an image using an `x` and `y` input signal.
  * It uses either a sinc-based band-limited resampling algorithm, or
  * bicubic interpolation, depending on the `zeroCrossings` parameter.
  *
  * All window defining parameters (`width`, `height`)
  * are polled once per matrix. All scanning and filter parameters are polled one per
  * output pixel.
  *
  * @param in             the image to scan
  * @param width          the width (number of columns) of the input matrix
  * @param height         the height (number of rows) of the input matrix
  * @param x              horizontal position of the dynamic scanning signal
  * @param y              vertical position of the dynamic scanning signal
  * @param next           a trigger that causes the UGen to read in a new image from `in`.
  * @param wrap           if non-zero, wraps coordinates around the input images boundaries.
  *                       __TODO:__ currently `wrap = 0` is broken if using sinc interpolation!
  * @param rollOff        the FIR anti-aliasing roll-off width. Between zero and one.
  * @param kaiserBeta     the FIR windowing function's parameter
  * @param zeroCrossings  the number of zero-crossings in the truncated and windowed sinc FIR.
  *                       If zero, algorithm uses bicubic interpolation instead.
  *
  * @see [[AffineTransform2D]]
  * @see [[Slices]]
  */
final case class ScanImage(in: GE, width: GE, height: GE, x: GE = 0, y: GE = 0, next: GE = 0, wrap: GE = 1,
                           rollOff: GE = 0.86, kaiserBeta: GE = 7.5, zeroCrossings: GE = 15)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(
      in.expand, width.expand, height.expand, x.expand, y.expand, next.expand, wrap.expand,
      rollOff.expand, kaiserBeta.expand, zeroCrossings.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, width, height, x, y, next, wrap, rollOff, kaiserBeta, zeroCrossings) = args
    stream.ScanImage(in = in.toDouble, width = width.toInt, height = height.toInt,
      x = x.toDouble, y = y.toDouble, next = next.toInt, wrap = wrap.toInt,
      rollOff = rollOff.toDouble, kaiserBeta = kaiserBeta.toDouble, zeroCrossings = zeroCrossings.toInt)
  }
}