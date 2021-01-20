/*
 *  Concat.scala
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
import de.sciss.fscape.stream.{BufD, BufI, BufL, StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object Concat extends ProductReader[Concat] {
  override def read(in: RefMapIn, key: String, arity: Int): Concat = {
    require (arity == 2)
    val _a = in.readGE()
    val _b = in.readGE()
    new Concat(_a, _b)
  }
}
/** Concatenates two signals. */
final case class Concat(a: GE, b: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit builder: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(a.expand, b.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit builder: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit builder: stream.Builder): StreamOut = {
    val Vec(a, b) = args
    if (a.isDouble || b.isDouble) {
      stream.Concat[Double, BufD](a = a.toDouble, b = b.toDouble)
    } else if (a.isLong || b.isLong) {
      stream.Concat[Long  , BufL](a = a.toLong  , b = b.toLong  )
    } else {
      assert (a.isInt)
      stream.Concat[Int   , BufI](a = a.toInt   , b = b.toInt   )
    }
  }
}