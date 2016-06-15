/*
 *  UnzipWindow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

final case class UnzipWindow(in: GE, size: GE = 1) extends GE.Lazy {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    UnzipWindowN(numOutputs = 2, in = in, size = size)
}

//final case class UnzipWindow(in: GE, size: GE = 1) extends UGenSource.MultiOut {
//  protected def makeUGens(implicit builder: UGenGraph.Builder): UGenInLike =
//    unwrap(Vector(in.expand, size.expand))
//
//  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
//    UGen.MultiOut(this, args, numOutputs = 2)
//
//  private[fscape] def makeStream(args: Vec[StreamIn])(implicit builder: stream.Builder): Vec[StreamOut] = {
//    val Vec(in, size) = args
//    stream.UnzipWindowN(numOutputs = 2, in = in.toDouble, size = size.toInt)
//  }
//}

final case class UnzipWindowN(numOutputs: Int, in: GE, size: GE = 1) extends UGenSource.MultiOut {
  protected def makeUGens(implicit builder: UGenGraph.Builder): UGenInLike =
    unwrap(Vector(in.expand, size.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.MultiOut(this, args, numOutputs = numOutputs, rest = numOutputs :: Nil)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit builder: stream.Builder): Vec[StreamOut] = {
    val Vec(in, size) = args
    stream.UnzipWindowN(numOutputs = numOutputs, in = in.toDouble, size = size.toInt)
  }
}