/*
 *  StrongestLocalMaxima.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
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

/** A peak detection UGen, useful for implementing the auto-correlation based pitch detection
  * method of Paul Boersma (1993).
  * Taking an already calculated auto-correlation of size `size`, the UGen looks
  * for local maxima within a given range.
  *
  * The UGen has two outputs. The first output gives the
  * lag times or periods of the `n` strongest peaks per window (to obtain a frequency, divide the sampling
  * rate by these lag times). The second output gives the intensities of these `n` candidates. If there
  * are less than `n` candidates, the empty slots are output as zeroes.
  *
  * @param in         the auto-correlation windows
  * @param size       the size of the auto-correlation windows. must be at least 2.
  * @param minLag     the minimum lag time in sample frames, corresponding to the maximum frequency accepted
  * @param maxLag     the maximum lag time in sample frames, corresponding to the minimum frequency accepted
  * @param thresh     the "voicing" threshold for considered for local maxima within `minLag` `maxLag`.
  * @param octaveCost a factor for favouring higher frequencies. use zero to turn off this feature.
  * @param num        number of candidate periods output. This is clipped to be at least 1.
  *
  * see [[PitchesToViterbi]]
  * see [[Viterbi]]
  */
final case class StrongestLocalMaxima(in: GE, size: GE, minLag: GE, maxLag: GE, thresh: GE = 0.0,
                                      octaveCost: GE = 0.0, num: GE = 14)
  extends UGenSource.MultiOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, minLag.expand, maxLag.expand, thresh.expand,
      octaveCost.expand, num.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.MultiOut(this, args, numOutputs = 2)

  def lags      : GE = ChannelProxy(this, 0)
  def strengths : GE = ChannelProxy(this, 1)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] = {
    val Vec(ac, size, minLag, maxLag, thresh, octaveCost, num) = args
    val (out0, out1) = stream.StrongestLocalMaxima(in = ac.toDouble, size = size.toInt,
      minLag = minLag.toInt, maxLag = maxLag.toInt,
      thresh = thresh.toDouble, octaveCost = octaveCost.toDouble, num = num.toInt)
    Vector[StreamOut](out0, out1)
  }
}
