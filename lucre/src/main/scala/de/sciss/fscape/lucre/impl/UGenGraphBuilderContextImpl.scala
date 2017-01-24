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

import de.sciss.fscape.lucre.UGenGraphBuilder.{IO, Input}
import de.sciss.lucre.stm.Sys

object UGenGraphBuilderContextImpl {
  final class Default[S <: Sys[S]](protected val fscape: FScape[S]) extends UGenGraphBuilderContextImpl[S]
}
trait UGenGraphBuilderContextImpl[S <: Sys[S]] extends UGenGraphBuilder.Context[S] {
  protected def fscape: FScape[S]

  def requestInput[Res](req: Input {type Value = Res}, io: IO[S])(implicit tx: S#Tx): Res = req match {
    case Input.Attribute(name) => ???
    case Input.Action   (name) => ???
    case _ => throw new IllegalStateException(s"Unsupported input request $req")
  }
}