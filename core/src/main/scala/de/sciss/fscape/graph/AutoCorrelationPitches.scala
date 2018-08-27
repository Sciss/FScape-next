/*
 *  AutoCorrelationPitches.scala
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

/** A pitch analysis UGen based on the auto-correlation method of Paul Boersma (1993).
  * It assumes that a non-normalized (!) auto-correlation in windows of size `size` has already
  * been calculated, was divided by the normalized auto-correlation of the windowing function,
  * and is passed in as input parameter `ar`. The UGen has two outputs. The first output gives the
  * lag times or periods of the `n` candidates per window (to obtain a frequency, divide the sampling
  * rate by these lag times). The second output gives the intensities of these `n` candidates.
  *
  * '''Note:''' We modify the algorithm here to take the non-normalized AC signal, which is normalized
  * internally. This is so that we can use the first AC coefficient, which per definition equals the power
  * in the signal, as an indicator for the "local absolute peak" used in Boersma's algorithm, which we
  * would otherwise have to pass in as a separate continuous parameter. As a consequence, the `thresh`
  * parameter which has the function of Boersma's 'VoicingThreshold', may need different values when
  * compared, for example, to Praat.
  *
  * '''Note:''' lags are currently calculated using parabolic interpolation only. A future version
  * might refine using sinc-interpolation, as Praat does.
  *
  * @param ac         the auto-correlation windows, ''not'' normalized.
  * @param size       the size of the auto-correlation windows. must be at least 2.
  * @param minLag     the minimum lag time in sample frames, corresponding to the maximum frequency accepted
  * @param maxLag     the maximum lag time in sample frames, corresponding to the minimum frequency accepted
  * @param thresh     the "voicing" threshold for considered for local maxima within `minLag` `maxLag`.
  *                   Note that internally, half of this value is used; this is so that you can normally
  *                   use the same threshold for `AutoCorrelationPitches` and `VibertiPitchPath`.
  * @param octaveCost a ratio for favouring higher frequencies
  * @param n          number of candidate periods output. This is clipped to be at least 2,
  *                   where one candidate (the last in the list) always refers to the unvoiced condition.
  */
final case class AutoCorrelationPitches(ac: GE, size: GE, minLag: GE, maxLag: GE, thresh: GE = 0.45,
                                        octaveCost: GE = 0.01, n: GE = 15)
  extends UGenSource.MultiOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(ac.expand, size.expand, minLag.expand, maxLag.expand, thresh.expand,
      octaveCost.expand, n.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.MultiOut(this, args, numOutputs = 2)

  def lags      : GE = ChannelProxy(this, 0)
  def strengths : GE = ChannelProxy(this, 1)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] = {
    val Vec(ac, size, minLag, maxLag, thresh, octaveCost, n) = args
    val (out0, out1) = stream.AutoCorrelationPitches(ac = ac.toDouble, size = size.toInt,
      minLag = minLag.toInt, maxLag = maxLag.toInt,
      thresh = thresh.toDouble, octaveCost = octaveCost.toDouble, n = n.toInt)
    Vector[StreamOut](out0, out1)
  }
}
