/*
 *  MatrixInMatrix.scala
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

object MatrixInMatrix extends ProductReader[MatrixInMatrix] {
  override def read(in: RefMapIn, key: String, arity: Int): MatrixInMatrix = {
    require (arity == 8)
    val _in           = in.readGE()
    val _rowsOuter    = in.readGE()
    val _columnsOuter = in.readGE()
    val _rowsInner    = in.readGE()
    val _columnsInner = in.readGE()
    val _rowStep      = in.readGE()
    val _columnStep   = in.readGE()
    val _mode         = in.readGE()
    new MatrixInMatrix(_in, _rowsOuter, _columnsOuter, _rowsInner, _columnsInner, _rowStep, _columnStep, _mode)
  }
}
/** Note: `mode` is not yet implemented. */
final case class MatrixInMatrix(in          : GE,
                                rowsOuter   : GE,
                                columnsOuter: GE,
                                rowsInner   : GE,
                                columnsInner: GE,
                                rowStep     : GE = 1,
                                columnStep  : GE = 1,
                                mode        : GE = 0,
                               )
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, rowsOuter.expand, columnsOuter.expand, rowsInner.expand, columnsInner.expand,
      rowStep.expand, columnStep.expand, mode.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, rowsOuter, columnsOuter, rowsInner, columnsInner, rowStep, columnStep, mode) = args
    stream.MatrixInMatrix(in = in.toDouble, rowsOuter = rowsOuter.toInt, columnsOuter = columnsOuter.toInt,
      rowsInner = rowsInner.toInt, columnsInner = columnsInner.toInt,
      rowStep = rowStep.toInt, columnStep = columnStep.toInt, mode = mode.toInt)
  }
}