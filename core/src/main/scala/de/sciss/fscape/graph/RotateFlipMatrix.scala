/*
 *  RotateFlipMatrix.scala
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

/** A UGen that can apply horizontal and vertical flip and 90-degree step rotations to a matrix.
  *
  * Unless mode is 90-degree rotation (4 or 5) and the matrix is not square, this needs one
  * internal matrix buffer, otherwise two internal matrix buffers are needed.
  *
  * '''Warning:''' window parameter modulation is currently not working correctly (issue #30)
  *
  * @param in       the matrix / matrices to rotate
  * @param rows     the number of rows in the input
  * @param columns  the number of columns in the input
  * @param mode     0: pass, 1: flip horizontally, 2: flip vertically, 3: rotate 180 degrees,
  *                 4: rotate clockwise, 8: rotate anti-clockwise. See the companion object
  *                 for constants. If you combine flipping and rotation, flipping is performed first,
  *                 so a mode of 5 means flip horizontally, followed by rotate clockwise.
  */
final case class RotateFlipMatrix(in: GE, rows: GE, columns: GE, mode: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, rows.expand, columns.expand, mode.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, rows, columns, mode) = args
    stream.RotateFlipMatrix(in = in.toDouble, rows = rows.toInt, columns = columns.toInt, mode = mode.toInt)
  }
}
object RotateFlipMatrix {
  final val Through   = 0
  final val FlipX     = 1
  final val FlipY     = 2
  final val Rot180    = FlipX | FlipY
  final val Rot90CW   = 4
  final val Rot90CCW  = 8
}