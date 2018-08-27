/*
 *  RunningMin.scala
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

/** A UGen that tracks the minimum value seen so far, until a trigger resets it.
  *
  * ''Note:'' This is different from SuperCollider's `RunningMin` UGen which outputs
  * the maximum of a sliding window instead of waiting for a reset trigger.
  * To track a sliding minimum, you can use `PriorityQueue`.
  *
  * @param in     the signal to track
  * @param trig   a trigger occurs when transitioning from non-positive to positive.
  *               At this point (an initially) the internal state is set to positive infinity.
  */
final case class RunningMin(in: GE, trig: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, trig.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, trig) = args
    stream.RunningMin(in = in.toDouble, trig = trig.toInt)
  }
}