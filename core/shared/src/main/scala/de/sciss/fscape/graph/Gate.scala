/*
 *  Gate.scala
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

object Gate extends ProductReader[Gate] {
  override def read(in: RefMapIn, key: String, arity: Int): Gate = {
    require (arity == 2)
    val _in   = in.readGE()
    val _gate = in.readGE()
    new Gate(_in, _gate)
  }
}
/** A UGen that passes through its input while the gate is open, and outputs zero while the gate is closed.
  *
  * @see [[FilterSeq]]
  * @see [[Latch]]
  */
final case class Gate(in: GE, gate: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, gate.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, gate) = args
    import in.tpe
    val out = stream.Gate[in.A, in.Buf](in = in.toElem, gate = gate.toInt)
    tpe.mkStreamOut(out)
  }
}