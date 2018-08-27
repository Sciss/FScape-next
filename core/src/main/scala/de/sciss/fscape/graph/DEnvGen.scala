/*
 *  DEnvGen.scala
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

/** An envelope generator UGen similar to SuperCollider's DemandEnvGen.
  *
  * For each parameter of the envelope (levels, durations and shapes), values are
  * polled every time a new segment starts. The UGen keeps running as long as
  * level inputs are provided. If any of the other inputs terminate early, their
  * last values will be used for successive segments (e.g. one can use a constant
  * length or shape).
  *
  * Note that as a consequence of having a "hot" `levels` input,
  * the target level of the last segment may not be reached, as the UGen would
  * terminate one sample earlier. As a workaround one can duplicate the last
  * level value with a length of 1.
  *
  * The difference to `DemandEnvGen` is that this UGen does not duplicate functionality in the form
  * of `levelScale`, `levelBias`, `timeScale`, and it does not provide `reset`
  * and `gate` functionality. Durations are given as lengths in sample frames.
  */
final case class DEnvGen(levels: GE, lengths: GE, shapes: GE = 1, curvatures: GE = 0.0)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(levels.expand, lengths.expand, shapes.expand, curvatures.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(levels, lengths, shapes, curvatures) = args
    stream.DEnvGen(levels = levels.toDouble, lengths = lengths.toLong, shapes = shapes.toInt,
      curvatures = curvatures.toDouble)
  }
}
