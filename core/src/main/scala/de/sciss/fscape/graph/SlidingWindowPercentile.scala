/*
 *  SlidingWindowPercentile.scala
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

/** A UGen that reports a percentile of a sliding window across every cell
  * of a window'ed input (such as an image).
  *
  * The UGen starts outputting values immediately, even if the `medianLen`
  * is not yet reached. This is because `medianLen` can be modulated (per input window).
  * If one wants to discard the initial values, use a `drop`, for example
  * for `medianLen/2 * winSize` frames.
  *
  * Note that for an even median length and no interpolation, the reported median
  * may be either the value at index `medianLen/2` or `medianLen/2 + 1` in the sorted window.
  *
  * All arguments but `in` are polled per input window. Changing the `frac` value
  * may cause an internal table rebuild and can thus be expensive.
  *
  * @param in         the window'ed input to analyze
  * @param winSize    the size of the input windows
  * @param medianLen  the length of the sliding median window (the filter window
  *                   applied to every cell of the input window)
  * @param frac       the percentile from zero to one. The default of 0.5 produces the median.
  * @param interp     if zero (default), uses nearest-rank, otherwise uses linear interpolation.
  *                   '''Note:''' currently not implemented, must be zero
  */
final case class SlidingWindowPercentile(in: GE, winSize: GE, medianLen: GE = 3, frac: GE = 0.5, interp: GE = 0)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, winSize.expand, medianLen.expand, frac.expand, interp.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, winSize, medianLen, frac, interp) = args
    stream.SlidingWindowPercentile(in = in.toDouble, winSize = winSize.toInt, medianLen = medianLen.toInt,
      frac = frac.toDouble, interp = interp.toInt)
  }
}