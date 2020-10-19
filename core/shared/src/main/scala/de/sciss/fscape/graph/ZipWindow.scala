/*
 *  ZipWindow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{BufD, BufI, BufL, StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

final case class ZipWindow(a: GE, b: GE, size: GE = 1) extends UGenSource.SingleOut {
  protected def makeUGens(implicit builder: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(a.expand, b.expand, size.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit builder: stream.Builder): StreamOut = {
    val Vec(a, b, size) = args
    if (a.isDouble || b.isDouble) {
      stream.ZipWindowN[Double, BufD](in = a.toDouble :: b.toDouble :: Nil, size = size.toInt)
    } else if (a.isLong || b.isLong) {
      stream.ZipWindowN[Long  , BufL](in = a.toLong   :: b.toLong   :: Nil, size = size.toInt)
    } else {
      stream.ZipWindowN[Int   , BufI](in = a.toInt    :: b.toInt    :: Nil, size = size.toInt)
    }
  }
}

final case class ZipWindowN(in: GE, size: GE = 1) extends UGenSource.SingleOut {
  protected def makeUGens(implicit builder: UGenGraph.Builder): UGenInLike =
    unwrap(this, size.expand +: in.expand.outputs)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit builder: stream.Builder): StreamOut = {
    val size +: in = args
    if (in.exists(_.isDouble)) {
      stream.ZipWindowN[Double, BufD](in = in.map(_.toDouble), size = size.toInt)
    } else if (in.exists(_.isLong)) {
      stream.ZipWindowN[Long  , BufL](in = in.map(_.toLong  ), size = size.toInt)
    } else {
      stream.ZipWindowN[Int   , BufI](in = in.map(_.toInt   ), size = size.toInt)
    }
  }
}