/*
 *  SetResetFF.scala
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

/** A flip-flop UGen with two inputs, one (set) triggering an output of 1, the other (reset)
  * triggering an output of 0. Subsequent triggers happening within the same input slot have
  * no effect. If both inputs receive a trigger at the same time, the reset input takes precedence.
  */
final case class SetResetFF(trig: GE, reset: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(trig.expand, reset.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(trig, reset) = args
    stream.SetResetFF(trig = trig.toInt, reset = reset.toInt)
  }
}
