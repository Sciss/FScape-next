/*
 *  RepeatWindow.scala
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

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen that repeats the contents of input windows a number of times.
  *
  * @param in   the input signal to group into windows of size `size` and
  *             repeat `num` times. If the input ends before a full window
  *             is filled, the last window is padded with zeroes to obtain
  *             an input window if size `size`
  * @param size the window size. one value is polled per iteration
  *             (outputting `num` windows of size `size`). the minimum
  *             `size` is one (it will be clipped to this).
  * @param num  the number of repetitions of the input windows. one value is polled per iteration
  *             (outputting `num` windows of size `size`). the minimum
  *             `num` is one (it will be clipped to this).
  */
final case class RepeatWindow(in: GE, size: GE = 1, num: GE = 2) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, num.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, num) = args
    import in.tpe
    val out = stream.RepeatWindow[in.A, in.Buf](in = in.toElem, size = size.toInt, num = num.toLong)
    tpe.mkStreamOut(out)
  }
}