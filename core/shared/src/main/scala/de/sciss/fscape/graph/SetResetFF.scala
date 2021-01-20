/*
 *  SetResetFF.scala
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

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object SetResetFF extends ProductReader[SetResetFF] {
  override def read(in: RefMapIn, key: String, arity: Int): SetResetFF = {
    require (arity == 2)
    val _set    = in.readGE()
    val _reset  = in.readGE()
    new SetResetFF(_set, _reset)
  }
}
/** A flip-flop UGen with two inputs, one (set) triggering an output of 1, the other (reset)
  * triggering an output of 0. Subsequent triggers happening within the same input slot have
  * no effect. If both inputs receive a trigger at the same time, the reset input takes precedence.
  *
  * Both inputs are "hot" and the UGen runs until both have been terminated.
  */
final case class SetResetFF(set: GE, reset: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(set.expand, reset.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(set, reset) = args
    stream.SetResetFF(set = set.toInt, reset = reset.toInt)
  }
}
