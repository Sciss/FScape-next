/*
 *  ARCWindow.scala
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
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** Automatic range control UGen. It traces the
  * range of a windowed input signal.
  *
  * If all values of a window are the same, the `lo` value is output.
  *
  * @param in     signal to adjust
  * @param size   window size of input
  * @param lo     desired lower margin of output
  * @param hi     desired upper margin of output
  * @param lag    lag or feedback coefficient
  */
final case class ARCWindow(in: GE, size: GE, lo: GE = 0.0, hi: GE = 1.0, lag: GE = 0.96)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, lo.expand, hi.expand, lag.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, lo, hi, lag) = args
    stream.ARCWindow(in = in.toDouble, size = size.toInt, lo = lo.toDouble, hi = hi.toDouble, lag = lag.toDouble)
  }
}