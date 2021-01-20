/*
 *  Pearson.scala
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

object Pearson extends ProductReader[Pearson] {
  override def read(in: RefMapIn, key: String, arity: Int): Pearson = {
    require (arity == 3)
    val _x    = in.readGE()
    val _y    = in.readGE()
    val _size = in.readGE()
    new Pearson(_x, _y, _size)
  }
}
/** A UGen that calculates the Pearson product-moment correlation coefficient of
  * two input matrices.
  *
  * @param x      first matrix
  * @param y      second matrix
  * @param size   matrix or window size
  */
final case class Pearson(x: GE, y: GE, size: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(x.expand, y.expand, size.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(x, y, size) = args
    stream.Pearson(x = x.toDouble, y = y.toDouble, size = size.toInt)
  }
}