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

import akka.stream.ClosedShape
import akka.stream.scaladsl.GraphDSL
import de.sciss.fscape.graph.{Constant, UGenProxy}
import de.sciss.fscape.stream.StreamIn

import scala.collection.breakOut
import scala.collection.immutable.{IndexedSeq => Vec}

object UGenGraph {
  trait Builder {
    def addUGen(ugen: UGen): Unit
    def visit[U](ref: AnyRef, init: => U): U
  }

  def build(graph: Graph)(implicit ctrl: stream.Control): UGenGraph = {
    val b = new BuilderImpl
    graph.sources.foreach { source =>
      source.force(b)
    }
    b.build
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

  private final class ConstantIndex(val peer: Constant) extends UGenInIndex {
    def makeEffective() = 0

    override def toString = s"ConstantIndex($peer)"
  }

  private final class UGenProxyIndex(val iu: IndexedUGenBuilder, val outIdx: Int) extends UGenInIndex {
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

  private final class BuilderImpl(implicit ctrl: stream.Control) extends Builder {
    private[this] var ugens     = Vector.empty[UGen]
    private[this] var sourceMap = Map.empty[AnyRef, Any]

    def build: UGenGraph = {
      val iUGens = indexUGens()
      buildStream(iUGens)
      new UGenGraph {
        def run(): Unit = ???

        def dispose(): Unit = ???
      }
    }

    // - converts to StreamIn objects that automatically insert stream broadcasters
    //   and dummy sinks
    private def buildStream(ugens: Vec[IndexedUGenBuilder]): Unit = {
      GraphDSL.create() { implicit dsl =>
        implicit val sb   = stream.Builder()
        ugens.foreach { iu =>
          val args: Vec[StreamIn] = iu.inputIndices.map {
            case c: ConstantIndex => c.peer
            case u: UGenInIndex =>
              ???
          } (breakOut)

          iu.ugen match {
            case ugen: UGen.SingleOut =>
              ugen.source.makeStream(args)
            case ugen: UGen.ZeroOut   =>
              ugen.source.makeStream(args)
            case ugen: UGen.MultiOut  =>
              ugen.source.makeStream(args)
          }
        }
        ClosedShape
      }
    }

    // - builds parent-child graph of UGens
    // - deletes no-op sub-trees
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
            val iui       = ugenMap(up.ugen)
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
trait UGenGraph {
  def run(): Unit

  def dispose(): Unit
}