/*
 *  AGCWindow.scala
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

/** Automatic gain control UGen. It traces the
  * gain of a windowed input signal.
  *
  * @param in     signal to adjust
  * @param size   window size of input
  * @param lo     desired lower margin of output
  * @param hi     desired upper margin of output
  * @param lag    lag or feedback coefficient
  */
final case class AGCWindow(in: GE, size: GE, lo: GE, hi: GE, lag: GE = 0.96) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(Vector(in.expand, size.expand, lo.expand, hi.expand, lag.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, lo, hi, lag) = args
    stream.AGCWindow(in = in.toDouble, size = size.toInt, lo = lo.toDouble, hi = hi.toDouble, lag = lag.toDouble)
  }
}