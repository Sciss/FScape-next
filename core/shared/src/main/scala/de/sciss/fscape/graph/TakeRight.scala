/*
 *  TakeRight.scala
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

object TakeRight extends ProductReader[TakeRight] {
  override def read(in: RefMapIn, key: String, arity: Int): TakeRight = {
    require (arity == 2)
    val _in     = in.readGE()
    val _length = in.readGE()
    new TakeRight(_in, _length)
  }
}
final case class TakeRight(in: GE, length: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, length.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, length) = args
    val inE = in.toElem
    import in.tpe
    val out = stream.TakeRight[in.A, in.Buf](in = inE, length = length.toInt)
    tpe.mkStreamOut(out)
  }
}