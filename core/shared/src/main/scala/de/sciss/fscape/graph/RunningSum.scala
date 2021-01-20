/*
 *  RunningSum.scala
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

object RunningSum extends ProductReader[RunningSum] {
  override def read(in: RefMapIn, key: String, arity: Int): RunningSum = {
    require (arity == 2)
    val _in   = in.readGE()
    val _gate = in.readGE()
    new RunningSum(_in, _gate)
  }
}
/** A UGen that integrates the values seen so far, until a trigger resets it.
  *
  * ''Note:'' This is different from SuperCollider's `RunningSum` UGen which outputs
  * the sum of a sliding window instead of waiting for a reset trigger.
  * To track a sliding sum, you can subtract a delayed version of `RunningSum` from
  * a non-delayed `RunningSum`.
  *
  * @param in     the signal to track
  * @param gate   a gate that when greater than zero (an initially) sets the
  *               the internal state is set to zero.
  */
final case class RunningSum(in: GE, gate: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, gate.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, gate) = args
    import in.tpe
    val out = stream.RunningSum[in.A, in.Buf](in = in.toElem, gate = gate.toInt)
    tpe.mkStreamOut(out)
  }
}