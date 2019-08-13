/*
 *  UGenGraphBuilderContextImpl.scala
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
package lucre
package impl

import de.sciss.fscape.lucre.UGenGraphBuilder.MissingIn
import de.sciss.fscape.lucre.{UGenGraphBuilder => UGB}
import de.sciss.lucre.expr.ExprLike
import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc
import de.sciss.synth.proc.{Runner, Universe}

object UGenGraphBuilderContextImpl {
  final class Default[S <: Sys[S]](protected val fscape: FScape[S],
                                   protected val attr: Runner.Attr[S])(implicit val universe: Universe[S])
    extends UGenGraphBuilderContextImpl[S]
}
trait UGenGraphBuilderContextImpl[S <: Sys[S]] extends UGenGraphBuilder.Context[S] {
  protected def fscape: FScape[S] // XXX TODO --- why not transactional? I think all UGB runs in one go
  protected def attr  : Runner.Attr[S]

  protected implicit def universe: Universe[S]

  def requestInput[Res](in: UGB.Input { type Value = Res }, io: UGB.IO[S] with UGenGraphBuilder)
                       (implicit tx: S#Tx): Res = in match {
    case i: UGB.Input.Attribute =>
      val aKey  = i.name
      // XXX TODO --- at some point we should 'merge' the two maps
      // WARNING: Scala compiler bug, cannot use `collect` with
      // `PartialFunction` here, only total function works.
      val peer: Option[Any] = attr.get(aKey).flatMap {
        case x: ExprLike[S, _]  => Some(x.value)
        case _                  => fscape.attr.get(aKey).flatMap {
          case x: ExprLike[S, _]  => Some(x.value)
          case _                  => None
        }
      }
      UGB.Input.Attribute.Value(peer)

    case i: UGB.Input.Action =>
      val aKey  = i.name
      val f     = fscape
      // XXX TODO at some point we should allow `IAct`
      // ... or actually just a `Runner` for any object
      val res   = f.attr.$[proc.Action](aKey).map { a =>
        new ActionRefImpl[S](aKey, tx.newHandle(f), tx.newHandle(a))
      }
      res.getOrElse(throw MissingIn(aKey))

    case i => throw new IllegalStateException(s"Unsupported input request $i")
  }
}