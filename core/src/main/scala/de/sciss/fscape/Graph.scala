/*
 *  Graph.scala
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

import scala.collection.immutable.{IndexedSeq => Vec}

object Graph {
  trait Builder {
    def addLazy(x: Lazy): Unit

    def removeLazy(x: Lazy): Unit
  }

  /** This is analogous to `SynthGraph.Builder` in ScalaCollider. */
  def builder: Builder  = builderRef.get()

  /** Installs a custom graph builder on the current thread,
    * during the invocation of a closure. This method is typically
    * called from other libraries which wish to provide a graph
    * builder other than the default.
    *
    * When the method returns, the previous graph builder has automatically
    * been restored. During the execution of the `body`, calling
    * `Graph.builder` will return the given `builder` argument.
    *
    * @param builder    the builder to install on the current thread
    * @param body       the body which is executed with the builder found through `Graph.builder`
    * @tparam A         the result type of the body
    * @return           the result of executing the body
    */
  def use[A](builder: Builder)(body: => A): A = {
    val old = builderRef.get()
    builderRef.set(builder)
    try {
      body
    } finally {
      builderRef.set(old)
    }
  }

  private[this] val builderRef: ThreadLocal[Builder] = new ThreadLocal[Builder] {
    override protected def initialValue: Builder = BuilderDummy
  }

  private[this] object BuilderDummy extends Builder {
    def addLazy   (x: Lazy): Unit = ()
    def removeLazy(x: Lazy): Unit = ()
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
    private var lazies = Vector.empty[Lazy]

    override def toString = s"fscape.Graph.Builder@${hashCode.toHexString}"

    def build = Graph(lazies)

    def addLazy(g: Lazy): Unit = lazies :+= g

    def removeLazy(g: Lazy): Unit =
      lazies = if (lazies.last == g) lazies.init else lazies.filterNot(_ == g)
  }
}

final case class Graph(sources: Vec[Lazy] /* , controlProxies: Set[ControlProxyLike] */) {
  def isEmpty : Boolean  = sources.isEmpty // && controlProxies.isEmpty
  def nonEmpty: Boolean  = !isEmpty

  // def expand(implicit ctrl: stream.Control): UGenGraph = UGenGraph.build(this)
}

