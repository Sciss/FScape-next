/*
 *  TranposeMatrix.scala
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

// XXX TODO --- we could probably use a disk-buffered variant as well for image processing

/** A UGen that transposes 2-dimensional matrices.
  * This is a 2-dimensional windowed process, where
  * each window has length `rows * columns`. Input
  * is assumed to be "left-to-right, top-to-bottom",
  * so the first samples constitute the first row,
  * the next samples constitute the second row, etc.
  *
  * The output matrix is transposed (rows and columns
  * exchanged). So an input of `[a, b, c, d, e, f]`
  * with `rows = 2` and `columns = 3` is interpreted
  * as `[[a, b, c], [d, e, f]]`, transposed as
  * `[[a, d], [b, e], [c, f]]` and output flat as
  * `[a, d, b, e, c, f]`.
  *
  * To rotate an image ninety
  * degrees clockwise, you would have `rows = height`
  * and `columns = width`.
  *
  * @param in       the input matrices
  * @param rows     the number of rows of the _input_
  * @param columns  the number of columns of the _input_
  */
final case class TransposeMatrix(in: GE, rows: GE, columns: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(Vector(in.expand, rows.expand, columns.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, rows, columns) = args
    stream.TransposeMatrix(in = in.toDouble, rows = rows.toInt, columns = columns.toInt)
  }
}