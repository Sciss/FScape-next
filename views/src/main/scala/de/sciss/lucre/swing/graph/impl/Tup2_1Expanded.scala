/*
 *  Tup2_1Expanded.scala
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

package de.sciss.lucre.swing.graph.impl

import de.sciss.lucre.{IExpr, ITargets, Txn}
import de.sciss.lucre.expr.graph.impl.MappedIExpr

/** N.B.: disposes the input `tup`! */
final class Tup2_1Expanded[T <: Txn[T], A, B](tup: IExpr[T, (A, B)], tx0: T)
                                                     (implicit targets: ITargets[T])
  extends MappedIExpr[T, (A, B), A](tup, tx0) {

  protected def mapValue(tupVal: (A, B))(implicit tx: T): A = tupVal._1

  override def dispose()(implicit tx: T): Unit = {
    super.dispose()
    tup.dispose()
  }
}
