/*
 *  PeakCentroid.scala
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
import de.sciss.fscape.UGenGraph.Builder
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object PeakCentroid1D extends ProductReader[PeakCentroid1D] {
  override def read(in: RefMapIn, key: String, arity: Int): PeakCentroid1D = {
    require (arity == 5)
    val _in       = in.readGE()
    val _size     = in.readGE()
    val _thresh1  = in.readGE()
    val _thresh2  = in.readGE()
    val _radius   = in.readGE()
    new PeakCentroid1D(_in, _size, _thresh1, _thresh2, _radius)
  }
}
/**
  * '''Warning:''' window parameter modulation is currently not working correctly (issue #30)
  */
final case class PeakCentroid1D(in: GE, size: GE, thresh1: GE = 0.5, thresh2: GE = 0.33, radius: GE = 1)
  extends GE.Lazy {

  protected def makeUGens(implicit b: Builder): UGenInLike = {
    val c   = PeakCentroid2D(in = in, width = size, height = 1, thresh1 = thresh1, thresh2 = thresh2, radius = radius)
    val tx  = c.translateX
    val p   = c.peak
    Seq(tx, p): GE
  }

  def translate: GE = ChannelProxy(this, 0)
  def peak     : GE = ChannelProxy(this, 1)
}

object PeakCentroid2D extends ProductReader[PeakCentroid2D] {
  override def read(in: RefMapIn, key: String, arity: Int): PeakCentroid2D = {
    require (arity == 6)
    val _in       = in.readGE()
    val _width    = in.readGE()
    val _height   = in.readGE()
    val _thresh1  = in.readGE()
    val _thresh2  = in.readGE()
    val _radius   = in.readGE()
    new PeakCentroid2D(_in, _width, _height, _thresh1, _thresh2, _radius)
  }
}
/**
  * '''Warning:''' window parameter modulation is currently not working correctly (issue #30)
  */
final case class PeakCentroid2D(in: GE, width: GE, height: GE, thresh1: GE = 0.5, thresh2: GE = 0.33, radius: GE = 1)
  extends UGenSource.MultiOut {

  def translateX: GE = ChannelProxy(this, 0)
  def translateY: GE = ChannelProxy(this, 1)
  def peak      : GE = ChannelProxy(this, 2)

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, width.expand, height.expand, thresh1.expand, thresh2.expand, radius.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.MultiOut(this, inputs = args, numOutputs = 3)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] = {
    val Vec(in, width, height, thresh1, thresh2, radius) = args
    val (tx, ty, p) = stream.PeakCentroid2D(in = in.toDouble, width = width.toInt, height = height.toInt,
      thresh1 = thresh1.toDouble, thresh2 = thresh2.toDouble, radius = radius.toInt)
    Vec(tx, ty, p)
  }
}