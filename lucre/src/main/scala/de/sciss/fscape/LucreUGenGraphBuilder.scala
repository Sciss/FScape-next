package de.sciss.fscape

import de.sciss.lucre.expr.Expr
import de.sciss.lucre.stm.Sys

object LucreUGenGraphBuilder {
  def build[S <: Sys[S]](f: FScape[S], graph: Graph)(implicit tx: S#Tx, ctrl: stream.Control): UGenGraph = {
    val b = new BuilderImpl(f)
    graph.sources.foreach { source =>
      source.force(b)
    }
    b.build
  }

  private final class BuilderImpl[S <: Sys[S]](f: FScape[S])(implicit tx: S#Tx, protected val ctrl: stream.Control)
    extends UGenGraph.BuilderLike with LucreUGenGraphBuilder {

    def requestAttribute(key: String): Option[Any] =
      f.attr.get(key) collect {
        case x : Expr[S, _] => x.value
        case other => other
      }
  }
}
trait LucreUGenGraphBuilder extends UGenGraph.Builder {
  def requestAttribute(key: String): Option[Any]
}