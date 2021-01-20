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

object GramSchmidtMatrix extends ProductReader[GramSchmidtMatrix] {
  override def read(in: RefMapIn, key: String, arity: Int): GramSchmidtMatrix = {
    require (arity == 4)
    val _in         = in.readGE()
    val _rows       = in.readGE()
    val _columns    = in.readGE()
    val _normalize  = in.readGE()
    new GramSchmidtMatrix(_in, _rows, _columns, _normalize)
  }
}
/** A UGen that orthogonalizes an input matrix
  * using the stabilized Gram-Schmidt algorithm.
  * It processes the row vectors per matrix by
  * making them orthogonal to one another, optionally
  * also orthonormal.
  *
  * @param in       the input matrices
  * @param rows     the number of rows of the ''input''
  * @param columns  the number of columns of the ''input''
  */
final case class GramSchmidtMatrix(in: GE, rows: GE, columns: GE, normalize: GE = 0)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, rows.expand, columns.expand, normalize.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, rows, columns, normalize) = args
    stream.GramSchmidtMatrix(in = in.toDouble, rows = rows.toInt, columns = columns.toInt, normalize = normalize.toInt)
  }
}