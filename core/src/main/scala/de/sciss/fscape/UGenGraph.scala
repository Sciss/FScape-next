/*
 *  UGenGraph.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape

import akka.NotUsed
import akka.stream.ClosedShape
import akka.stream.scaladsl.{GraphDSL, RunnableGraph}
import de.sciss.fscape.graph.{Constant, UGenProxy}
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object UGenGraph {
  trait Builder {
    def addUGen(ugen: UGen): Unit
    def visit[U](ref: AnyRef, init: => U): U
  }

  def build(graph: Graph)(implicit ctrl: stream.Control): UGenGraph = {
    val b = new BuilderImpl
    var g0 = graph
    while (g0.nonEmpty) {
      g0 = Graph {
        g0.sources.foreach { source =>
          source.force(b)
        }
      }
    }
    b.build
  }

  // ---- IndexedUGen ----
  final class IndexedUGenBuilder(val ugen: UGen /* , var index: Int */, var effective: Boolean) {
    var children    : Array[List[IndexedUGenBuilder]]   = Array.fill(ugen.numOutputs)(Nil)
    var inputIndices: List[UGenInIndex]                 = Nil
    var index       : Int                               = -1

    override def toString = s"Idx($ugen, $effective) : richInputs = $inputIndices"
  }

  private[fscape] sealed trait UGenInIndex {
    def makeEffective(): Int
  }

  private[fscape] final class ConstantIndex(val peer: Constant) extends UGenInIndex {
    def makeEffective() = 0

    override def toString: String = peer.toString
  }

  private[fscape] final class UGenProxyIndex(val iu: IndexedUGenBuilder, val outIdx: Int) extends UGenInIndex {
    def makeEffective(): Int = {
      if (!iu.effective) {
        iu.effective = true
        var numEff   = 1
        iu.inputIndices.foreach(numEff += _.makeEffective())
        numEff
      } else 0
    }

    override def toString = s"$iu[$outIdx]"
  }

  private final class BuilderImpl extends BuilderLike

  // - converts to StreamIn objects that automatically insert stream broadcasters
  //   and dummy sinks
  def buildStream(ugens: Vec[IndexedUGenBuilder])(implicit ctrl: stream.Control): RunnableGraph[NotUsed] = {
    // empty graphs are not supported by Akka
    if (ugens.isEmpty) throw new IllegalStateException("Graph is empty")
    val _graph = GraphDSL.create() { implicit dsl =>
      implicit val sb: stream.Builder = stream.Builder()

      var ugenOutMap = Map.empty[IndexedUGenBuilder, Array[StreamIn]]

      ugens.foreach { iu =>
        val args: Vec[StreamIn] = iu.inputIndices.iterator.map {
          case c: ConstantIndex   => c.peer
          case u: UGenProxyIndex  => ugenOutMap(u.iu)(u.outIdx)
        } .toIndexedSeq

        @inline def add(value: Array[StreamIn]): Unit = {
          // println(s"map += $iu -> ${value.mkString("[", ", ", "]")}")
          ugenOutMap += iu -> value
        }

        iu.ugen match {
          case ugen: UGen.SingleOut =>
            val out         = ugen.source.makeStream(args)
            val numChildren = iu.children(0).size
            val value       = Array(out.toIn(numChildren))
            add(value)

          case ugen: UGen.MultiOut  =>
            val outs: Vec[StreamOut] = ugen.source.makeStream(args)
            val value: Array[StreamIn] = outs.iterator.zipWithIndex.map { case (out, outIdx) =>
              val numChildren = iu.children(outIdx).size
              out.toIn(numChildren)
            } .toArray
            add(value)

          case ugen: UGen.ZeroOut   =>
            ugen.source.makeStream(args)
        }
      }
      ClosedShape
    }
    RunnableGraph.fromGraph(_graph)
  }


  // - builds parent-child graph of UGens
  // - deletes no-op sub-trees
  def indexUGens(ugens: Vec[UGen]): Vec[IndexedUGenBuilder] = {
    var numIneffective  = ugens.size
    val indexedUGens    = ugens.map { ugen =>
      val eff = ugen.hasSideEffect
      if (eff) numIneffective -= 1
      new IndexedUGenBuilder(ugen, eff)
    }

    val ugenMap: Map[AnyRef, IndexedUGenBuilder] = indexedUGens.iterator.map(iu => (iu.ugen, iu)).toMap
    indexedUGens.foreach { iu =>
      iu.inputIndices = iu.ugen.inputs.iterator.map {
        case c: Constant =>
          new ConstantIndex(c)

        case up: UGenProxy =>
          val iui = ugenMap(up.ugen)
          iui.children(up.outputIndex) ::= iu
          new UGenProxyIndex(iui, up.outputIndex)
      } .toList
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

  private[fscape] trait BuilderLike extends Builder {
    // ---- abstract ----

//    implicit protected def ctrl: stream.Control

    // ---- impl ----

    private[this] var _ugens    = Vector.empty[UGen]
    // private[this] val ugenSet   = mutable.Set.empty[UGen]
    private[this] var sourceMap = Map.empty[AnyRef, Any]

    protected final def ugens: Vec[UGen] = _ugens

    def build(implicit ctrl: stream.Control): UGenGraph = {
      val iUGens  = indexUGens(_ugens)
      val rg      = buildStream(iUGens)
      UGenGraph(rg)
    }

    private def printSmart(x: Any): String = x match {
      case u: UGen          => u.name
      // case p: ChannelProxy  => s"${printSmart(p.elem)}.\\(${p.index})"
      case _                => x.toString
    }

    @inline
    private def printRef(ref: AnyRef): String = {
      val hash = ref.hashCode.toHexString
//      ref match {
//        case p: Product => s"${p.productPrefix}@$hash"
//        case _ => hash
//      }
      hash
    }

    def visit[U](ref: AnyRef, init: => U): U = {
      logGraph(s"visit  ${printRef(ref)}")
      sourceMap.getOrElse(ref, {
        logGraph(s"expand ${printRef(ref)}...")
        val exp = init
        logGraph(s"...${printRef(ref)} -> ${exp.hashCode.toHexString} ${printSmart(exp)}")
        sourceMap += ref -> exp
        exp
      }).asInstanceOf[U] // not so pretty...
    }

    def addUGen(ugen: UGen): Unit = {
      // Where is this check in ScalaCollider? Have we removed it (why)?
      // N.B.: We do not use UGen equality any longer in FScape because
      // we might need to feed the same structure into different sinks
      // that read at different speeds, so we risk to block the graph
      // (Imagine a `DC(0.0)` going into two entirely different places!)

      // if (ugenSet.add(ugen)) {
        _ugens :+= ugen
        logGraph(s"addUGen ${ugen.name} @ ${ugen.hashCode.toHexString} ${if (ugen.isIndividual) "indiv" else ""}")
      // } else {
      //  logGraph(s"addUGen ${ugen.name} @ ${ugen.hashCode.toHexString} - duplicate")
      // }
    }
  }
}
final case class UGenGraph(runnable: RunnableGraph[NotUsed])