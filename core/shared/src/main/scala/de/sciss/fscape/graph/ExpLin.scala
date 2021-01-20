/*
 *  ExpLin.scala
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

package de.sciss.fscape.graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}
import de.sciss.fscape.{GE, UGen, UGenGraph, UGenIn, UGenInLike, UGenSource, stream}

import scala.collection.immutable.{IndexedSeq => Vec}

object ExpLin extends ProductReader[ExpLin] {
  override def read(in: RefMapIn, key: String, arity: Int): ExpLin = {
    require (arity == 5)
    val _in       = in.readGE()
    val _inLow    = in.readGE()
    val _inHigh   = in.readGE()
    val _outLow   = in.readGE()
    val _outHigh  = in.readGE()
    new ExpLin(_in, _inLow, _inHigh, _outLow, _outHigh)
  }
}
final case class ExpLin(in: GE, inLow: GE, inHigh: GE, outLow: GE, outHigh: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, inLow.expand, inHigh.expand, outLow.expand, outHigh.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, inLow, inHigh, outLow, outHigh) = args
    stream.ExpLin(in = in.toDouble, inLow = inLow.toDouble, inHigh = inHigh.toDouble,
      outLow = outLow.toDouble, outHigh = outHigh.toDouble)
  }
}
