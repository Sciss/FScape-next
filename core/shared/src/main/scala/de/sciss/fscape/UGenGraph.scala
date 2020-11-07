/*
 *  UGenGraph.scala
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

import akka.NotUsed
import akka.stream.ClosedShape
import akka.stream.scaladsl.{GraphDSL, RunnableGraph}
import de.sciss.fscape.graph.{Constant, UGenProxy}
import de.sciss.fscape.stream.{StreamIn, StreamOut}
import de.sciss.fscape.Log.{graph => logGraph}

import scala.annotation.elidable
import scala.collection.immutable.{IndexedSeq => Vec}

object UGenGraph {
  trait Builder {
    def addUGen(ugen: UGen): Unit

    def visit[U](ref: AnyRef, init: => U): U

    def expandNested(graph: Graph): Int
  }

  def build(graph: Graph)(implicit ctrl: stream.Control): UGenGraph = {
    val b = new Impl
    b.expandNested(graph)
    b.build
  }

  // ---- IndexedUGen ----

  final class IndexedUGenBuilder(val ugen: UGen, val layer: Int, var effective: Boolean) {
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

  // - converts to StreamIn objects that automatically insert stream broadcasters
  //   and dummy sinks
  def buildStream(ugens: Vec[IndexedUGenBuilder])(implicit ctrl: stream.Control): RunnableGraph[NotUsed] = {
    // empty graphs are not supported by Akka
    if (ugens.isEmpty) throw new IllegalStateException("Graph is empty")
    val _graph = GraphDSL.create() { implicit dsl =>
      implicit val sb: stream.Builder.Settable = stream.Builder()

      var ugenOutMap  = Map.empty[IndexedUGenBuilder, Array[StreamIn]]
      var oldLayer    = -1

      ugens.foreach { iu =>
        val args: Vec[StreamIn] = iu.inputIndices.iterator.map {
          case c: ConstantIndex   => c.peer
          case u: UGenProxyIndex  => ugenOutMap(u.iu)(u.outIdx)
        } .toIndexedSeq

        @inline def add(value: Array[StreamIn]): Unit = {
          // println(s"map += $iu -> ${value.mkString("[", ", ", "]")}")
          ugenOutMap += iu -> value
        }

        if (iu.layer != oldLayer) {
          sb.layer = iu.layer
          oldLayer = iu.layer
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

  /////////////////////////////////////////////

  private final class Impl extends Basic

  private final class UGenInLayer(val ugen: UGen, val layer: Int)

  private[fscape] trait Basic extends Builder /* with Graph.Builder */ {
    builder =>

    // ---- abstract ----

    // ---- impl ----

    private[this] var layer = 0

    private[this] var _ugens = Vector.empty[UGenInLayer]
    // private[this] val ugenSet   = mutable.Set.empty[UGen]
    protected var sourceMap = Map.empty[AnyRef, Any]

//    protected final def ugens: Vec[UGen] = _ugens

    // - builds parent-child graph of UGens
    // - deletes no-op sub-trees

    protected def indexUGens(): Vec[IndexedUGenBuilder] = {
      val ugens = _ugens
      var numIneffective  = ugens.size
      val indexedUGens    = ugens.map { ul =>
        val eff = ul.ugen.hasSideEffect
        if (eff) numIneffective -= 1
        new IndexedUGenBuilder(ul.ugen, layer = ul.layer, effective = eff)
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

    def build(implicit ctrl: stream.Control): UGenGraph = {
      val iUGens  = indexUGens()
      val rg      = buildStream(iUGens)
      UGenGraph(rg)
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

    final def visit[U](ref: AnyRef, init: => U): U = {
      log(this, s"visit  ${printRef(ref)}")
      val res = sourceMap.getOrElse(ref, {
        log(this, s"expand ${smartRef(ref)}...")
        val exp: Any = init
        sourceMap += ref -> exp
        log(this, s"...${smartRef(ref)} -> ${exp.hashCode.toHexString} ${printSmart(exp)}")
        exp
      })
      res.asInstanceOf[U] // not so pretty...
    }

    def addUGen(ugen: UGen): Unit = {
      // Where is this check in ScalaCollider? Have we removed it (why)?
      // N.B.: We do not use UGen equality any longer in FScape because
      // we might need to feed the same structure into different sinks
      // that read at different speeds, so we risk to block the graph
      // (Imagine a `DC(0.0)` going into two entirely different places!)

      // if (ugenSet.add(ugen)) {
        _ugens :+= new UGenInLayer(ugen, layer)
        log(this, s"addUGen ${ugen.name} @ ${ugen.hashCode.toHexString} ${if (ugen.isIndividual) "indiv" else ""} (layer $layer)")
      // } else {
      //  log(this, s"addUGen ${ugen.name} @ ${ugen.hashCode.toHexString} - duplicate")
      // }
    }

    def expandNested(graph: Graph): Int = {
      val _layer    = allocId()
      val oldLayer  = layer
      layer         = _layer
      var g0 = graph
      while (g0.nonEmpty) {
        g0 = Graph {
          g0.sources.foreach { source =>
            source.force(builder)
          }
        }
      }
      layer = oldLayer
      _layer
    }

    private[this] var idCount = 0

    /* Allocates a unique increasing identifier. */
    private def allocId(): Int = {
      val res = idCount
      idCount += 1
      res
    }
  }

  private def smartRef(ref: AnyRef): String = {
    val t = new Throwable
    t.fillInStackTrace()
    val trace = t.getStackTrace
    val opt = trace.collectFirst {
      case ste if (ste.getMethodName == "force" || ste.getMethodName == "expand") && ste.getFileName != "Lazy.scala" =>
        val clz = ste.getClassName
        val i   = clz.lastIndexOf(".") + 1
        val j   = clz.lastIndexOf("@", i)
        val s   = if (j < 0) clz.substring(i) else clz.substring(i, j)
        s"$s@${ref.hashCode().toHexString}"
    }
    opt.getOrElse(ref.hashCode.toHexString)
  }

  final var showLog = false

  private def printSmart(x: Any): String = x match {
    case u: UGen  => u.name
    case _        => x.toString
  }

  @elidable(elidable.CONFIG) private def log(builder: Basic, what: => String): Unit =
    if (showLog) logGraph.debug(s"<${builder.toString}> $what")
}
final case class UGenGraph(runnable: RunnableGraph[NotUsed])