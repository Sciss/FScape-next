/*
 *  UGenGraphBuilder.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre

import de.sciss.lucre.expr.Expr
import de.sciss.lucre.stm.Sys

object UGenGraphBuilder {
  def build[S <: Sys[S]](f: FScape[S], graph: Graph)(implicit tx: S#Tx, ctrl: stream.Control): UGenGraph = {
    val b = new BuilderImpl(f)
    graph.sources.foreach { source =>
      source.force(b)
    }
    b.build
  }

  private final class BuilderImpl[S <: Sys[S]](f: FScape[S])(implicit tx: S#Tx, protected val ctrl: stream.Control)
    extends UGenGraph.BuilderLike with UGenGraphBuilder {

    def requestAttribute(key: String): Option[Any] =
      f.attr.get(key) collect {
        case x : Expr[S, _] => x.value
        case other => other
      }
  }
}
trait UGenGraphBuilder extends UGenGraph.Builder {
  def requestAttribute(key: String): Option[Any]
}