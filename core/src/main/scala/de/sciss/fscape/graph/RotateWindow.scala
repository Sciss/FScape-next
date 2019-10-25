/*
 *  RotateWindow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
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

/** A UGen that rotates the contents of a window, wrapping around its boundaries.
  * For example, it can be used to align the phases prior to FFT so that the sample
  * that was formerly in the centre of the window moves to the beginning of the window.
  *
  * '''Warning:''' window parameter modulation is currently not working correctly (issue #30)
  *
  * @param in     the signal to window and resize
  * @param size   the input window size
  * @param amount the rotation amount in sample frames. Positive values "move" the contents
  *               to the right, negative values "move" the contents to the left. The amount
  *               is taken modulus `size`.
  */
final case class RotateWindow(in: GE, size: GE, amount: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, amount.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, amount) = args
    stream.RotateWindow(in = in.toDouble, size = size.toInt, amount = amount.toInt)
  }
}