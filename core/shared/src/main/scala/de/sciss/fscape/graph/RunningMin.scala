/*
 *  RunningMin.scala
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

object RunningMin extends ProductReader[RunningMin] {
  override def read(in: RefMapIn, key: String, arity: Int): RunningMin = {
    require (arity == 2)
    val _in   = in.readGE()
    val _gate = in.readGE()
    new RunningMin(_in, _gate)
  }
}
/** A UGen that tracks the minimum value seen so far, until a trigger resets it.
  *
  * ''Note:'' This is different from SuperCollider's `RunningMin` UGen which outputs
  * the maximum of a sliding window instead of waiting for a reset trigger.
  * To track a sliding minimum, you can use `PriorityQueue`.
  *
  * @param in     the signal to track
  * @param gate   a gate that when greater than zero (an initially) sets the
  *               the internal state is set to positive infinity.
  */
final case class RunningMin(in: GE, gate: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, gate.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, gate) = args
    import in.tpe
    val out = stream.RunningMin[in.A, in.Buf](in = in.toElem, gate = gate.toInt)
    tpe.mkStreamOut(out)
  }
}