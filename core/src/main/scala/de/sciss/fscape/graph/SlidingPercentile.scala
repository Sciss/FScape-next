/*
 *  SlidingPercentile.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
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

/** A UGen that reports a percentile of a sliding window across its input.
  * The UGen starts outputting values immediately, even if the window `size`
  * is not yet reached. This is because `size` can be modulated.
  * If one wants to discard the initial values, use a `drop`.
  *
  * Note that for an even window size and no interpolation, the reported median
  * may be either the value at index `size/2` or `size/2 + 1` in the sorted window.
  *
  * All arguments are polled at the same rate. Changing the `frac` value
  * may cause an internal table rebuild and can thus be expensive.
  *
  * @param in     the input to analyze
  * @param size   the size of the sliding window
  * @param frac   the percentile from zero to one. The default of 0.5 produces the median.
  * @param interp if zero (default), uses nearest-rank, otherwise uses linear interpolation.
  *               '''Note:''' currently not implemented, must be zero
  */
final case class SlidingPercentile(in: GE, size: GE, frac: GE = 0.5, interp: GE = 0)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, frac.expand, interp.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, frac, interp) = args
    stream.SlidingPercentile(in = in.toDouble, size = size.toInt, frac = frac.toDouble, interp = interp.toInt)
  }
}