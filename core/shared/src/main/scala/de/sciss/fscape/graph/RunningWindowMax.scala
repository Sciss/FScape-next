/*
 *  RunningWindowMax.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
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
  * @param gate   a gate signal that clears the internal state. When a gate is open (> 0),
  *               the ''currently processed'' window is reset altogether
  *               until its end, beginning accumulation again from the successive
  *               window.
  */
final case class RunningWindowMax(in: GE, size: GE, gate: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, gate.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, gate) = args
    import in.tpe
    val out = stream.RunningWindowMax[in.A, in.Buf](in = in.toElem, size = size.toInt, gate = gate.toInt)
    tpe.mkStreamOut(out)
  }
}