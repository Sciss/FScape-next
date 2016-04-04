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
  def builder : Builder  = ???

  def apply(thunk: => Any): Graph = {
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

final case class Graph(sources: Vec[Lazy] /* , controlProxies: Set[ControlProxyLike] */) {
  def isEmpty : Boolean  = sources.isEmpty // && controlProxies.isEmpty
  def nonEmpty: Boolean  = !isEmpty
  def expand: Module = ???
}

