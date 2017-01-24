/*
 *  UGenGraphBuilderContextImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre
package impl

import de.sciss.fscape.lucre.UGenGraphBuilder.{IO, Input, MissingIn}
import de.sciss.lucre.expr.Expr
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc
import de.sciss.synth.proc.{GenContext, WorkspaceHandle}

object UGenGraphBuilderContextImpl {
  final class Default[S <: Sys[S]](protected val fscape: FScape[S])(implicit context: GenContext[S])
    extends UGenGraphBuilderContextImpl[S] {

    protected def cursor    : stm.Cursor[S]       = context.cursor
    protected def workspace : WorkspaceHandle[S]  = context.workspaceHandle
  }
}
trait UGenGraphBuilderContextImpl[S <: Sys[S]] extends UGenGraphBuilder.Context[S] {
  protected def fscape: FScape[S]

  protected implicit def cursor   : stm.Cursor[S]
  protected implicit def workspace: WorkspaceHandle[S]

  def requestInput[Res](req: Input {type Value = Res}, io: IO[S])(implicit tx: S#Tx): Res = req match {
    case Input.Attribute(aKey) =>
      val f = fscape
      val peer = f.attr.get(aKey) collect {
        case x: Expr[S, _]  => x.value
        case other          => other
      }
      Input.Attribute.Value(peer)

    case Input.Action(aKey) =>
      val f = fscape
      val res = f.attr.$[proc.Action](aKey).map { a =>
        new ActionRefImpl(aKey, tx.newHandle(f), tx.newHandle(a))
      }
      res.getOrElse(throw MissingIn(aKey))

    case _ => throw new IllegalStateException(s"Unsupported input request $req")
  }
}