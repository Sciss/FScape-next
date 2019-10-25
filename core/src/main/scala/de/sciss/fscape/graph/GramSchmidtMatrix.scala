/*
 *  TranposeMatrix.scala
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

/** A UGen that orthogonalizes an input matrix
  * using the stabilized Gram-Schmidt algorithm.
  * It processes the row vectors per matrix by
  * making them orthogonal to one another, optionally
  * also orthonormal.
  *
  * '''Warning:''' window parameter modulation is currently not working correctly (issue #30)
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