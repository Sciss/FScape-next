/*
 *  Distinct.scala
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

object Distinct extends ProductReader[Distinct] {
  override def read(in: RefMapIn, key: String, arity: Int): Distinct = {
    require (arity == 1)
    val _in = in.readGE()
    new Distinct(_in)
  }
}
/** A UGen that collects distinct values from the input and outputs them
  * in the original order.
  *
  * '''Note:''' Currently keeps all seen values in memory.
  */
final case class Distinct(in: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in) = args
    import in.tpe
    val out = stream.Distinct[in.A, in.Buf](in = in.toElem)
    tpe.mkStreamOut(out)
  }
}