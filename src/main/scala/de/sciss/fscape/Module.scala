/*
 *  Module.scala
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

object Module {
  trait Builder {
    def addUGen(ugen: UGen): Unit
    def visit[U](ref: AnyRef, init: => U): U
  }

  private[this] trait AbstractBuilder extends Builder {
    def build: Module
  }

  private[this] final val builderRef = new ThreadLocal[AbstractBuilder] {
    override protected def initialValue = BuilderDummy
  }

  private object BuilderDummy extends AbstractBuilder {
    def build: Module = outOfContext

    def addUGen(ugen: UGen) = ()

    def visit[U](ref: AnyRef, init: => U): U = outOfContext

    private def outOfContext: Nothing = sys.error("Out of context")
  }

  /** This is analogous to `UGenGraph.Builder` in ScalaCollider. */
  def builder: Builder = builderRef.get()

  def build(graph: Graph): Module = {
    val old = builderRef.get()
    val b   = new BuilderImpl
    builderRef.set(b)
    try {
      graph.sources.foreach { source =>
        source.force(b)
      }
      b.build

    } finally {
      builderRef.set(old)
    }
  }

  private[this] final class BuilderImpl extends AbstractBuilder {
    private[this] var ugens     = Vector.empty[UGen]
    private[this] var sourceMap = Map.empty[AnyRef, Any]

    def build: Module = {
      // XXX TODO -- optimise; for now just return all ugens unsorted
      Module(ugens)
    }

    def visit[U](ref: AnyRef, init: => U): U = {
      // log(s"visit  ${ref.hashCode.toHexString}")
      sourceMap.getOrElse(ref, {
        // log(s"expand ${ref.hashCode.toHexString}...")
        val exp = init
        // log(s"...${ref.hashCode.toHexString} -> ${exp.hashCode.toHexString} ${printSmart(exp)}")
        sourceMap += ref -> exp
        exp
      }).asInstanceOf[U] // not so pretty...
    }

    def addUGen(ugen: UGen): Unit = {
      ugens :+= ugen
      // log(s"addUGen ${ugen.name} @ ${ugen.hashCode.toHexString} ${if (ugen.isIndividual) "indiv" else ""}")
    }
  }
}
final case class Module(ugens: Vec[UGen]) {
  def run(): Unit = ???

  def dispose(): Unit = {
    ???
  }
}