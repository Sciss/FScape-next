/*
 *  PeakCentroid.scala
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

import de.sciss.fscape.UGenGraph.Builder
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

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

final case class PeakCentroid2D(in: GE, width: GE, height: GE, thresh1: GE = 0.5, thresh2: GE = 0.33, radius: GE = 1)
  extends UGenSource.MultiOut {

  def translateX: GE = ChannelProxy(this, 0)
  def translateY: GE = ChannelProxy(this, 1)
  def peak      : GE = ChannelProxy(this, 2)

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = unwrap(in.expand.outputs)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.MultiOut(this, inputs = args, numOutputs = 3)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] = {
    val Vec(in, width, height, thresh1, thresh2, radius) = args
    val (tx, ty, p) = stream.PeakCentroid2D(in = in.toDouble, width = width.toInt, height = height.toInt,
      thresh1 = thresh1.toDouble, thresh2 = thresh2.toDouble, radius = radius.toInt)
    Vec(tx, ty, p)
  }
}