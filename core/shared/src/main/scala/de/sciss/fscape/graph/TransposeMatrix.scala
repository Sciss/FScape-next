/*
 *  TranposeMatrix.scala
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
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

// XXX TODO --- we could probably use a disk-buffered variant as well for image processing

object TransposeMatrix extends ProductReader[TransposeMatrix] {
  override def read(in: RefMapIn, key: String, arity: Int): TransposeMatrix = {
    require (arity == 3)
    val _in       = in.readGE()
    val _rows     = in.readGE()
    val _columns  = in.readGE()
    new TransposeMatrix(_in, _rows, _columns)
  }
}
/** A UGen that transposes 2-dimensional matrices.
  * This is a 2-dimensional windowed process, where
  * each window has length `rows * columns`. Input
  * is assumed to be "left-to-right, top-to-bottom",
  * so the first samples constitute the first row,
  * the next samples constitute the second row, etc.
  *
  * '''Note:''' The UGens takes up twice the matrix size of memory.
  *
  * The output matrix is transposed (rows and columns
  * exchanged). So an input of `(a, b, c, d, e, f)`
  * with `rows = 2` and `columns = 3` is interpreted
  * as `((a, b, c), (d, e, f))`, transposed as
  * `((a, d), (b, e), (c, f))` and output flat as
  * `(a, d, b, e, c, f)`.
  *
  * To rotate an image ninety
  * degrees clockwise, you would have `rows = height`
  * and `columns = width`.
  *
  * @param in       the input matrices
  * @param rows     the number of rows of the _input_
  * @param columns  the number of columns of the _input_
  *
  * @see [[RotateFlipMatrix]]
  */
final case class TransposeMatrix(in: GE, rows: GE, columns: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, rows.expand, columns.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, rows, columns) = args
    import in.tpe
    val out = stream.TransposeMatrix[in.A, in.Buf](in = in.toElem, rows = rows.toInt, columns = columns.toInt)
    tpe.mkStreamOut(out)
  }
}