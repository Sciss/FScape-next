/*
 *  Resample.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
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

/** A band-limited resampling UGen.
  *
  * It uses an internal table for the anti-aliasing filter.
  * Table resolution is currently fixed at 4096 filter samples per zero crossing and
  * linear interpolation in the FIR table, but the total filter length can be specified
  * through the `zeroCrossings` parameter. Note: If filter parameters are changed, the
  * table must be recalculated which is very expensive. However, `factor` modulation
  * is efficient.
  *
  * __Note:__ Unlike most other UGens, all parameters but `in` are read at "output rate".
  * That is particular important for `factor` modulation. For each frame output, one
  * frame from `factor` is consumed.
  *
  * @param in             the signal to resample
  * @param factor         the resampling factor, where values greater than one
  *                       mean the signal is stretched (sampling-rate increases or pitch lowers)
  *                       and values less than one mean the signal is condensed (sampling-rate decreases
  *                       or pitch rises)
  * @param minFactor      the minimum expected resampling factor, which controls
  *                       the amount of buffering needed for the input signal. This is used at
  *                       initialization time only. The default
  *                       value of zero makes the UGen settles on the first `factor` value encountered.
  *                       It is possible to use a value actually higher than the lowest provided
  *                       `factor`, in order to limit the buffer usage. In that case, the FIR anti-aliasing
  *                       filter will be truncated.
  * @param rollOff        the FIR anti-aliasing roll-off width
  * @param kaiserBeta     the FIR windowing function's parameter
  * @param zeroCrossings  the number of zero-crossings in the truncated and windowed sinc FIR.
  */
final case class Resample(in: GE, factor: GE, minFactor: GE = 0,
                          rollOff: GE = 0.86, kaiserBeta: GE = 7.5, zeroCrossings: GE = 15)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, factor.expand, minFactor.expand,
      rollOff.expand, kaiserBeta.expand, zeroCrossings.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, factor, minFactor, rollOff, kaiserBeta, zeroCrossings) = args
    stream.Resample(in = in.toDouble, factor = factor.toDouble, minFactor = minFactor.toDouble,
      rollOff = rollOff.toDouble, kaiserBeta = kaiserBeta.toDouble, zeroCrossings = zeroCrossings.toInt)
  }
}