package de.sciss.fscape

import de.sciss.fscape.ugen.Lazy

import scala.collection.immutable.{IndexedSeq => Vec}

object FScapeGraph {
  trait Builder {
    def addLazy(x: Lazy): Unit
  }

  /** This is analogous to `SynthGraph.Builder` in ScalaCollider. */
  def builder : Builder  = ???

  def apply(thunk: => Any): FScapeGraph = {
    ???
    //    val b   = new BuilderImpl
    //    val old = builders.get()
    //    builders.set(b)
    //    try {
    //      thunk
    //      b.build
    //    } finally {
    //      builders.set(old) // BuilderDummy
    //    }
  }
}

final case class FScapeGraph(sources: Vec[Lazy] /* , controlProxies: Set[ControlProxyLike] */) {
  def isEmpty : Boolean  = sources.isEmpty // && controlProxies.isEmpty
  def nonEmpty: Boolean  = !isEmpty
  def expand: FScapeProcess = ???
}

