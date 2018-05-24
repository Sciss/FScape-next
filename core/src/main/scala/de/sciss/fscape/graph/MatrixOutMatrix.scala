/*
 *  MatrixOutMatrix.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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

/** A UGen that stitches together sequences of sub-matrices to a larger matrix.
  * The matrix dimensions and offsets are updated per "matrix chunk" which is are
  * `columnsOuter/columnNum` input matrices of size `rowsInner * columnsInner`.
  * In other words, the UGen has no
  * intrinsic knowledge of the height of the output matrix.
  *
  * For example, if the input matrices are of size (5, 6) (rows, columns), and we want to assemble the
  * cells (1, 1), (1, 2), (1, 3), (2, 1), (2, 2), (2, 3), that is copped matrices of size (2, 3)
  * beginning at the second row and second column, and we want the outer matrix to have 9 columns,
  * so that each three input matrices appear horizontally next to each other, the settings would be
  * `rowsInner = 5`, `columnsInner = 6`, `columnsOuter = 9`, `rowOff = 1`, `columnOff = 1`,
  * `rowNum = 2`, `columnNum = 3`.
  *
  * For more complex behaviour, such as skipping particular rows or columns, `ScanImage` can be used.
  *
  * @param in           the sequence of smaller matrices
  * @param rowsInner    height of the input matrices
  * @param columnsInner width of input matrices
  * @param columnsOuter width of the output matrix. Must be an integer multiple of `columnNum`.
  * @param rowOff       offset in rows within the input matrices, where copying
  *                     to the output matrix begins
  * @param columnOff    offset in columns within the input matrices, where copying
  *                     to the output matrix begins
  * @param rowNum       number of rows to copy from each input matrix
  * @param columnNum    number of columns to copy from each input matrix.
  */
final case class MatrixOutMatrix(in: GE, rowsInner: GE, columnsInner: GE, columnsOuter: GE,
                                 rowOff: GE = 0, columnOff: GE = 0,
                                 rowNum: GE = 1, columnNum: GE = 1)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, rowsInner.expand, columnsInner.expand, columnsOuter.expand, 
      rowOff.expand, columnOff.expand, rowNum.expand, columnNum.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, rowsInner, columnsInner, columnsOuter, rowOff, columnOff, rowNum, columnNum) = args
    stream.MatrixOutMatrix(in = in.toDouble, rowsInner = rowsInner.toInt, columnsInner = columnsInner.toInt,
      columnsOuter = columnsOuter.toInt,
      rowOff = rowOff.toInt, columnOff = columnOff.toInt,
      rowNum = rowNum.toInt, columnNum = columnNum.toInt)
  }
}