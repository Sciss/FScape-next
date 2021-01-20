/*
 *  TakeWhile.scala
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

object TakeWhile extends ProductReader[TakeWhile] {
  override def read(in: RefMapIn, key: String, arity: Int): TakeWhile = {
    require (arity == 2)
    val _in = in.readGE()
    val _p  = in.readGE()
    new TakeWhile(_in, _p)
  }
}
final case class TakeWhile(in: GE, p: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, p.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, p) = args
    val inE = in.toElem
    import in.tpe
    val out = stream.TakeWhile[in.A, in.Buf](in = inE, p = p.toInt)
    tpe.mkStreamOut(out)
  }
}