/*
 *  Fold.scala
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

object Fold extends ProductReader[Fold] {
  override def read(in: RefMapIn, key: String, arity: Int): Fold = {
    require (arity == 3)
    val _in = in.readGE()
    val _lo = in.readGE()
    val _hi = in.readGE()
    new Fold(_in, _lo, _hi)
  }
}
final case class Fold(in: GE, lo: GE, hi: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, lo.expand, hi.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, lo, hi) = args
    if (in.isInt && lo.isInt && hi.isInt) {
      stream.Fold.int   (in = in.toInt   , lo = lo.toInt   , hi = hi.toInt   )
    } else if ((in.isInt || in.isLong) && (lo.isInt || lo.isLong) && (hi.isInt || hi.isLong)) {
      stream.Fold.long  (in = in.toLong  , lo = lo.toLong  , hi = hi.toLong  )
    } else {
      stream.Fold.double(in = in.toDouble, lo = lo.toDouble, hi = hi.toDouble)
    }
  }
}