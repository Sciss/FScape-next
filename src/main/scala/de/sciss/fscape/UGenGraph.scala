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

import de.sciss.fscape.graph.{Constant, UGenProxy}

import scala.collection.{breakOut, mutable}
import scala.collection.immutable.{IndexedSeq => Vec}

object UGenGraph {
  trait Builder {
    def addUGen(ugen: UGen): Unit
    def visit[U](ref: AnyRef, init: => U): U

//    implicit def streamControl: stream.Control
//    implicit def streamBuilder: stream.GBuilder
  }

  private trait AbstractBuilder extends Builder {
    def build: UGenGraph
  }

  private[this] final val builderRef = new ThreadLocal[AbstractBuilder] {
    override protected def initialValue = BuilderDummy
  }

  private object BuilderDummy extends AbstractBuilder {
    def build: UGenGraph = outOfContext

    def addUGen(ugen: UGen) = ()

    def visit[U](ref: AnyRef, init: => U): U = outOfContext

    private def outOfContext: Nothing = sys.error("Out of context")
  }

//  /** This is analogous to `UGenGraph.Builder` in ScalaCollider. */
//  def builder: Builder = builderRef.get()

  def build(graph: Graph): UGenGraph = {
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

  // ---- IndexedUGen ----
  private final class IndexedUGenBuilder(val ugen: UGen /* , var index: Int */, var effective: Boolean) {
    var children      = Array.fill(ugen.numOutputs)(List.empty[IndexedUGenBuilder]) // mutable.Buffer.empty[IndexedUGenBuilder]
    var inputIndices  = List.empty[UGenInIndex]

    override def toString = s"IndexedUGen($ugen, $effective) : richInputs = $inputIndices"
  }

  private trait UGenInIndex {
    def makeEffective(): Int
  }

  private final class ConstantIndex(c: Constant) extends UGenInIndex {
    def makeEffective() = 0

    override def toString = s"ConstantIndex($c)"
  }

  private final class UGenProxyIndex(iu: IndexedUGenBuilder, outIdx: Int) extends UGenInIndex {
    def makeEffective(): Int = {
      if (!iu.effective) {
        iu.effective = true
        var numEff   = 1
        iu.inputIndices.foreach(numEff += _.makeEffective())
        numEff
      } else 0
    }

    override def toString = s"UGenProxyIndex($iu, $outIdx)"
  }

  private final class BuilderImpl extends AbstractBuilder {
    private[this] var ugens     = Vector.empty[UGen]
    private[this] var sourceMap = Map.empty[AnyRef, Any]

    def build: UGenGraph = {
      val iUGens = indexUGens()
      UGenGraph(ugens)
    }

    // - builds parent-child graph of UGens
    // - deletes no-op sub-trees
    // - converts to StreamIn objects that automatically insert stream broadcasters
    //   and dummy sinks
    private def indexUGens(): Vec[IndexedUGenBuilder] = {
      var numIneffective  = ugens.size
      val indexedUGens    = ugens.map { ugen =>
        val eff = ugen.hasSideEffect
        if (eff) numIneffective -= 1
        new IndexedUGenBuilder(ugen, eff)
      }

      val ugenMap: Map[AnyRef, IndexedUGenBuilder] = indexedUGens.map(iu => (iu.ugen, iu))(breakOut)
      indexedUGens.foreach { iu =>
        iu.inputIndices = iu.ugen.inputs.map {
          case c: Constant =>
            new ConstantIndex(c)

          case up: UGenProxy =>
            val iui       = ugenMap(up.source)
            iui.children(up.outputIndex) ::= iu
            new UGenProxyIndex(iui, up.outputIndex)
        } (breakOut)
        if (iu.effective) iu.inputIndices.foreach(numIneffective -= _.makeEffective())
      }
      val filtered: Vec[IndexedUGenBuilder] =
        if (numIneffective == 0)
          indexedUGens
        else
          indexedUGens.collect {
            case iu if iu.effective =>
              for (outputIndex <- iu.children.indices) {
                iu.children(outputIndex) = iu.children(outputIndex).filter(_.effective)
              }
              iu
          }

      filtered
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
final case class UGenGraph(ugens: Vec[UGen]) {
  def run(): Unit = ???

  def dispose(): Unit = {
    ???
  }
}