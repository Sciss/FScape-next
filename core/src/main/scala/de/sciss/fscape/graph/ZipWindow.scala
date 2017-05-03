/*
 *  ZipWindow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

final case class ZipWindow(a: GE, b: GE, size: GE = 1) extends UGenSource.SingleOut {
  protected def makeUGens(implicit builder: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(a.expand, b.expand, size.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit builder: stream.Builder): StreamOut = {
    val Vec(a, b, size) = args
    stream.ZipWindow(a = a.toDouble, b = b.toDouble, size = size.toInt)
  }
}

final case class ZipWindowN(in: GE, size: GE = 1) extends UGenSource.SingleOut {
  protected def makeUGens(implicit builder: UGenGraph.Builder): UGenInLike =
    unwrap(this, size.expand +: in.expand.outputs)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit builder: stream.Builder): StreamOut = {
    val size +: in = args
    stream.ZipWindowN(in = in.map(_.toDouble), size = size.toInt)
  }
}