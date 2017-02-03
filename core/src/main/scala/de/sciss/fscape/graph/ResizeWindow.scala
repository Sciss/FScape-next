/*
 *  ResizeWindow.scala
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

/** A UGen that resizes the windowed input signal by trimming each
  * windows boundaries (if `start` is greater than zero
  * or `stop` is less than zero) or padding the boundaries
  * with zeroes (if `start` is less than zero or `stop` is
  * greater than zero). The output window size is thus
  * `size - start + stop`.
  *
  * @param in     the signal to window and resize
  * @param size   the input window size
  * @param start  the delta window size at the output window's beginning
  * @param stop   the delta window size at the output window's ending
  */
final case class ResizeWindow(in: GE, size: GE, start: GE = 0, stop: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, start.expand, stop.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, start, stop) = args
    stream.ResizeWindow(in = in.toDouble, size = size.toInt, start = start.toInt, stop = stop.toInt)
  }
}