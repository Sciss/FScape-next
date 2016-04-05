/*
 *  Graph.scala
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

import scala.collection.immutable.{IndexedSeq => Vec}

object Graph {
  trait Builder {
    def addLazy(x: Lazy): Unit
  }

  /** This is analogous to `SynthGraph.Builder` in ScalaCollider. */
  def builder: Builder  = builderRef.get()

  private[this] val builderRef = new ThreadLocal[Builder] {
    override protected def initialValue = BuilderDummy
  }

  private[this] object BuilderDummy extends Builder {
    def addLazy(x: Lazy): Unit = ()
  }

  def apply(thunk: => Any): Graph = {
    val b   = new BuilderImpl
    val old = builderRef.get()
    builderRef.set(b)
    try {
      thunk
      b.build
    } finally {
      builderRef.set(old) // BuilderDummy
    }
  }

  private[this] final class BuilderImpl extends Builder {
    private val lazies = Vec.newBuilder[Lazy]

    override def toString = s"fscape.Graph.Builder@${hashCode.toHexString}"

    def build = Graph(lazies.result())

    def addLazy(g: Lazy): Unit = lazies += g
  }
}

final case class Graph(sources: Vec[Lazy] /* , controlProxies: Set[ControlProxyLike] */) {
  def isEmpty : Boolean  = sources.isEmpty // && controlProxies.isEmpty
  def nonEmpty: Boolean  = !isEmpty
  def expand: Module = Module.build(this)
}

