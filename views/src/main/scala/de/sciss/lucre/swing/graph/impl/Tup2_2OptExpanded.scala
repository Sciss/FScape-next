package de.sciss.lucre.swing.graph.impl

import de.sciss.lucre.{IExpr, ITargets, Txn}
import de.sciss.lucre.expr.graph.impl.MappedIExpr

/** N.B.: disposes the input `tup`! */
final class Tup2_2OptExpanded[T <: Txn[T], A, B](tup: IExpr[T, (A, Option[B])], default: B, tx0: T)
                                                (implicit targets: ITargets[T])
  extends MappedIExpr[T, (A, Option[B]), B](tup, tx0) {

  protected def mapValue(tupVal: (A, Option[B]))(implicit tx: T): B = tupVal._2.getOrElse(default)

  override def dispose()(implicit tx: T): Unit = {
    super.dispose()
    tup.dispose()
  }
}