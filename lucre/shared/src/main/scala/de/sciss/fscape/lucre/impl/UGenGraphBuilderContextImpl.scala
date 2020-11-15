/*
 *  UGenGraphBuilderContextImpl.scala
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
package lucre
package impl

import de.sciss.fscape.lucre.UGenGraphBuilder.MissingIn
import de.sciss.fscape.lucre.{UGenGraphBuilder => UGB}
import de.sciss.lucre.ExprLike
import de.sciss.lucre.Txn
import de.sciss.proc.{FScape, Runner, Universe}

object UGenGraphBuilderContextImpl {
  final class Default[T <: Txn[T]](protected val fscape: FScape[T],
                                   val attr: Runner.Attr[T])(implicit val universe: Universe[T])
    extends UGenGraphBuilderContextImpl[T]
}
trait UGenGraphBuilderContextImpl[T <: Txn[T]] extends UGenGraphBuilder.Context[T] {
  protected def fscape: FScape[T] // XXX TODO --- why not transactional? I think all UGB runs in one go

  protected implicit def universe: Universe[T]

  def requestInput[Res](in: UGB.Input { type Value = Res }, io: UGB.IO[T] with UGenGraphBuilder)
                       (implicit tx: T): Res = in match {
    case i: UGB.Input.Attribute =>
      val aKey  = i.name
      // XXX TODO --- at some point we should 'merge' the two maps
      // WARNING: Scala compiler bug, cannot use `collect` with
      // `PartialFunction` here, only total function works.
      val peer: Option[Any] = attr.get(aKey) match {
        case Some(x: ExprLike[T, _])  => Some(x.value)
        case _                        => fscape.attr.get(aKey) match {
          case Some(x: ExprLike[T, _])  => Some(x.value)
          case _                        => None
        }
      }
      UGB.Input.Attribute.Value(peer) // IntelliJ highlight bug

    case i: UGB.Input.Action =>
      val aKey  = i.name
      val f     = fscape
      f.attr.get(aKey) match {
        case Some(obj) =>
          val objH = tx.newHandle(obj)
          new ActionRefImpl[T](aKey, tx.newHandle(f), objH) // IntelliJ highlight bug

        case None =>
          throw MissingIn(aKey)
      }

    case i => throw new IllegalStateException(s"Unsupported input request $i")
  }
}