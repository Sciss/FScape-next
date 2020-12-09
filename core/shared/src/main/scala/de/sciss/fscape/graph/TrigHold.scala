/*
 *  TrigHold.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
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

/** A UGen that holds an input trigger signal for a given duration.
  * When a trigger is received from `in`, the output changes from zero
  * to one for the given `length` amount of frames. If a new trigger
  * arrives within this period, the internal length counter is reset
  * (the high state is kept for further `length` frames).
  *
  * @param  in      an input trigger or gate signal. Whenever the signal
  *                 is positive, the internal hold state is reset. If a strict
  *                 trigger is needed instead of a gate signal, a `Trig` UGen
  *                 can be inserted.
  * @param  length  the number of sample frames to hold the high output once
  *                 a high input is received.
  *                 A new value is polled whenever `in` is high.
  * @param  clear   an auxiliary trigger or gate signal that resets the
  *                 output to low if there is currently no high `in`.
  *                 This signal is read synchronously with `in.`
  *                 If both `clear` and `in` are high at a given point,
  *                 then the UGen outputs high but has its length counter
  *                 reset.
  */
final case class TrigHold(in: GE, length: GE, clear: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, length.expand, clear.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, length, clear) = args
    stream.TrigHold(in = in.toInt, length = length.toLong, clear = clear.toInt)
  }
}
