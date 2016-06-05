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

import akka.NotUsed
import akka.stream.ClosedShape
import akka.stream.scaladsl.{GraphDSL, RunnableGraph}
import de.sciss.fscape.graph.{Constant, UGenProxy}
import de.sciss.fscape.stream.StreamIn

import scala.annotation.switch
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

    override def toString = s"Idx($ugen, $effective) : richInputs = $inputIndices"
  }

  private trait UGenInIndex {
    def makeEffective(): Int
  }

  private final class ConstantIndex(val peer: Constant) extends UGenInIndex {
    def makeEffective() = 0

    override def toString = peer.toString
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

    override def toString = s"$iu[$outIdx]"
  }

  private final class BuilderImpl(implicit ctrl: stream.Control) extends Builder {
    private[this] var ugens     = Vector.empty[UGen]
    private[this] var sourceMap = Map.empty[AnyRef, Any]

    def build: UGenGraph = {
      val iUGens  = indexUGens()
      val rg      = buildStream(iUGens)
      UGenGraph(rg)
    }

    // - converts to StreamIn objects that automatically insert stream broadcasters
    //   and dummy sinks
    private def buildStream(ugens: Vec[IndexedUGenBuilder]): RunnableGraph[NotUsed] = {
      val _graph = GraphDSL.create() { implicit dsl =>
        implicit val sb = stream.Builder()

        var ugenOutMap = Map.empty[IndexedUGenBuilder, Array[StreamIn]]

        ugens.foreach { iu =>
          val args: Vec[StreamIn] = iu.inputIndices.map {
            case c: ConstantIndex   => c.peer
            case u: UGenProxyIndex  => ugenOutMap(u.iu)(u.outIdx)
          } (breakOut)

          def mkOut(outlet: stream.OutD, numChildren: Int): StreamIn = (numChildren: @switch) match {
            case 0 =>
              // XXX TODO -- we got to attach a dummy sink here
              StreamIn.unused
            case 1 => StreamIn.single(outlet)
            case n => StreamIn.multi (outlet, numChildren)
          }

          @inline def add(value: Array[StreamIn]): Unit = {
            println(s"map += $iu -> ${value.mkString("[", ", ", "]")}")
            ugenOutMap += iu -> value
          }

          iu.ugen match {
            case ugen: UGen.SingleOut =>
              val out         = ugen.source.makeStream(args)
              val numChildren = iu.children(0).size
              val value       = Array(mkOut(out.peer, numChildren))
              add(value)

            case ugen: UGen.MultiOut  =>
              val outs  = ugen.source.makeStream(args)
              val value = outs.zipWithIndex.map { case (out, outIdx) =>
                val numChildren = iu.children(outIdx).size
                mkOut(out.peer, numChildren)
              } (breakOut) : Array[StreamIn]
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
final case class UGenGraph(runnable: RunnableGraph[NotUsed])