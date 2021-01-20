/*
 *  UnzipWindow.scala
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
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object UnzipWindow extends ProductReader[UnzipWindow] {
  override def read(in: RefMapIn, key: String, arity: Int): UnzipWindow = {
    require (arity == 3)
    val _in   = in.readGE()
    val _size = in.readGE()
    new UnzipWindow(_in, _size)
  }
}
final case class UnzipWindow(in: GE, size: GE = 1) extends GE.Lazy {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    UnzipWindowN(numOutputs = 2, in = in, size = size)
}

object UnzipWindowN extends ProductReader[UnzipWindowN] {
  override def read(in: RefMapIn, key: String, arity: Int): UnzipWindowN = {
    require (arity == 3)
    val _numOutputs = in.readInt()
    val _in         = in.readGE()
    val _size       = in.readGE()
    new UnzipWindowN(_numOutputs, _in, _size)
  }
}
final case class UnzipWindowN(numOutputs: Int, in: GE, size: GE = 1) extends UGenSource.MultiOut {
  protected def makeUGens(implicit builder: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.MultiOut(this, args, numOutputs = numOutputs, adjuncts = Adjunct.Int(numOutputs) :: Nil)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit builder: stream.Builder): Vec[StreamOut] = {
    val Vec(in, size) = args
    import in.tpe
    val outs = stream.UnzipWindowN[in.A, in.Buf](numOutputs = numOutputs, in = in.toElem, size = size.toInt)
    outs.map(tpe.mkStreamOut)
  }
}