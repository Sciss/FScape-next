/*
 *  RunningWindowMax.scala
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

/** A UGen that like `RunningMax` calculates the maximum observed value of the
  * running input. However, it operates on entire windows, i.e. it outputs
  * windows that contain the maximum elements of all the past windows observed.
  *
  * @param in     the windowed signal to monitor
  * @param size   the window size. This should normally be a constant. If modulated,
  *               the internal buffer will be re-allocated, essentially causing
  *               a reset trigger.
  * @param trig   a trigger signal that clears the internal state. When a trigger
  *               is observed, the _currently processed_ window is reset altogether
  *               until its end, beginning accumulation again from the successive
  *               window. Normally one will thus want to emit a trigger in sync
  *               with the _start_ of each window. Emitting multiple triggers per
  *               window does not have any effect different from emitting the
  *               first trigger in each window.
  */
final case class RunningWindowMax(in: GE, size: GE, trig: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, trig.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, trig) = args
    stream.RunningWindowMax(in = in.toDouble, size = size.toInt, trig = trig.toInt)
  }
}