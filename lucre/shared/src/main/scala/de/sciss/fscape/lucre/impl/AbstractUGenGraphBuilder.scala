/*
 *  AbstractUGenGraphBuilder.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre
package impl

import java.util

import de.sciss.fscape.graph.{ConstantD, ConstantI, ConstantL}
import de.sciss.proc.FScape.Output
import de.sciss.fscape.lucre.UGenGraphBuilder.{Complete, Context, IO, Incomplete, Input, Key, MissingIn, OutputRef, OutputResult, State}
import de.sciss.fscape.stream.Control
import de.sciss.lucre.Txn
import de.sciss.serial.DataOutput

trait AbstractUGenGraphBuilder[T <: Txn[T]]
  extends UGenGraph.Basic with UGenGraphBuilder with IO[T] { builder =>

  // ---- abstract ----

  protected def context: Context[T]

  protected def requestOutputImpl(reader: Output.Reader): Option[OutputResult[T]]

  // ---- impl ----

  private[this] var _acceptedInputs   = Map.empty[Key, Map[Input, Input#Value]]
  private[this] var _outputs          = List.empty[OutputResult[T]] // in reverse order here

  final def acceptedInputs: Map[Key, Map[Input, Input#Value]] = _acceptedInputs
  final def outputs       : List[OutputResult[T]]             = _outputs

  private[this] var tx: T = _

  final def requestInput(req: Input): req.Value = {
    // we pass in `this` and not `in`, because that way the context
    // can find accepted inputs that have been added during the current build cycle!
    val res   = context.requestInput[req.Value](req, this)(tx)  // IntelliJ highlight bug
    val key   = req.key
    val map0  = _acceptedInputs.getOrElse(key, Map.empty)
    val map1  = map0 + (req -> res)
    _acceptedInputs += key -> map1
    //      logAural(s"acceptedInputs += ${req.key} -> $res")
    res
  }

  final def requestOutput(reader: Output.Reader): Option[OutputRef] = {
    val res = requestOutputImpl(reader)
    res.foreach { ref =>
      _outputs ::= ref
    }
    res
  }

  final def tryBuild(g: Graph)(implicit tx: T, ctrl: Control): State[T] = {
    this.tx = tx
    expandNested(g)
    tryBuild()
  }

  private def tryBuild()(implicit ctrl: Control): State[T] =
    try {
      val iUGens = indexUGens()
      new Complete[T] {
        private def calcStructure(): Long = {
          // val t1 = Txntem.currentTimeMillis()
          var idx = 0
          val out = DataOutput()
          out.writeInt(iUGens.size)
          iUGens.foreach { iu =>
            assert(iu.index == -1)
            iu.index = idx
            val ugen = iu.ugen
            out.writeUTF(ugen.name)
            val ins  = iu.inputIndices
            out.writeShort(ugen.inputs.size)
            ins.foreach {
              // UGenIn = [UGenProxy = [UGen.SingleOut, UGenOutProxy], Constant = [ConstantI, ConstantD, ConstantL]]
              case ci: UGenGraph.ConstantIndex =>
                ci.peer match {
                  case ConstantI(v) =>
                    out.writeByte(1)
                    out.writeInt(v)
                  case ConstantD(v) =>
                    out.writeByte(2)
                    out.writeDouble(v)
                  case ConstantL(v) =>
                    out.writeByte(3)
                    out.writeLong(v)
                }

              case pi: UGenGraph.UGenProxyIndex =>
                val refIdx = pi.iu.index
                assert(refIdx >= 0)
                out.writeByte(0)
                out.writeInt(refIdx)
                out.writeShort(pi.outIdx)
            }

            val adjuncts = ugen.adjuncts
            if (adjuncts.isEmpty) {
              out.writeShort(0)
            } else {
              out.writeShort(adjuncts.size)
              adjuncts.foreach(_.write(out))
            }

            idx += 1
          }

          val bytes = out.toByteArray
          val res = util.Arrays.hashCode(bytes) & 0x00000000FFFFFFFFL // XXX TODO use real 64-bit or 128-bit hash
          // val t2 = Txntem.currentTimeMillis()
          // println(s"calcStructure took ${t2 - t1}ms")
          res
        }

        private def calcStream(): UGenGraph = {
          val rg = UGenGraph.buildStream(iUGens)
          UGenGraph(rg)
        }

        lazy val structure  : Long                              = calcStructure()
        lazy val graph      : UGenGraph                         = calcStream()
        val acceptedInputs  : Map[Key, Map[Input, Input#Value]] = builder._acceptedInputs
        val outputs         : List[OutputResult[T]]             = builder._outputs.reverse
      }
    } catch {
      case MissingIn(key) =>
        new Incomplete[T] {
          val rejectedInputs: Set[String]                       = Set(key)
          val acceptedInputs: Map[Key, Map[Input, Input#Value]] = builder._acceptedInputs
          val outputs       : List[OutputResult[T]]             = builder._outputs.reverse
        }
    }
}
