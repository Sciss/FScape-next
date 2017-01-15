/*
 *  UGenGraphBuilder.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre

import java.util

import de.sciss.fscape.graph.{BinaryOp, Constant, ConstantD, ConstantI, ConstantL, UnaryOp}
import de.sciss.fscape.lucre.FScape.Output
import de.sciss.fscape.lucre.UGenGraphBuilder.{ActionRef, OutputRef}
import de.sciss.fscape.lucre.graph.Attribute
import de.sciss.fscape.lucre.impl.OutputImpl
import de.sciss.fscape.stream.Control
import de.sciss.lucre.expr.Expr
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Obj, Sys}
import de.sciss.serial.DataOutput
import de.sciss.synth.proc
import de.sciss.synth.proc.{SoundProcesses, WorkspaceHandle}

import scala.util.control.ControlThrowable

object UGenGraphBuilder {
  def get(b: UGenGraph.Builder): UGenGraphBuilder = b match {
    case ub: UGenGraphBuilder => ub
    case _ => sys.error("Out of context expansion")
  }

  def build[S <: Sys[S]](f: FScape[S], graph: Graph)(implicit tx: S#Tx, cursor: stm.Cursor[S],
                                                     workspace: WorkspaceHandle[S],
                                                     ctrl: Control): State[S] = {
    val b = new BuilderImpl(f)
    var g0 = graph
    while (g0.nonEmpty) {
      g0 = Graph {
        g0.sources.foreach { source =>
          source.force(b)
        }
      }
    }
    b.tryBuild()
  }

  trait IO[S <: Sys[S]] {
    def acceptedInputs: Set[String]

    /** Current set of used outputs (scan keys to number of channels).
      * This is guaranteed to only grow during incremental building, never shrink.
      */
    def outputs: Map[String, (Obj.Type, OutputResult[S])]
  }

  sealed trait State[S <: Sys[S]] extends IO[S] {
    def rejectedInputs: Set[String]

    def isComplete: Boolean
  }

  trait Incomplete[S <: Sys[S]] extends State[S] {
    final def isComplete = false
  }

  trait Complete[S <: Sys[S]] extends State[S] {
    final def isComplete = true

    /** Structural hash, lazily calculated from `Vec[UGen]` */
    def structure: Long

    /** Runnable stream graph, lazily calculated from `Vec[UGen]` */
    def graph: UGenGraph

    final def rejectedInputs: Set[String] = Set.empty
  }

  // ---- resolve ----

  def canResolve(in: GE): Either[String, Unit] = {
    in match {
      case _: Constant        => Right(())
      case _: Attribute       => Right(())
      case UnaryOp (_, a   )  => canResolve(a)
      case BinaryOp(_, a, b)  =>
        for {
          _ <- canResolve(a).right
          _ <- canResolve(b).right
        } yield ()

//      case _: NumChannels         => Right(())
      case _                      => Left(s"Element: $in")
    }
  }

  def resolve(in: GE, builder: UGenGraphBuilder): Either[String, Constant] = {
    in match {
      case c: Constant => Right(c)
      case a: Attribute =>
        builder.requestAttribute(a.key).fold[Either[String, Constant]] {
          a.default.fold[Either[String, Constant]] {
            Left(s"Missing attribute for key: ${a.key}")
          } {
            case Attribute.Scalar(c)                  => Right(c)
            case Attribute.Vector(cs) if cs.size == 1 => Right(cs.head)
            case other => Left(s"Cannot use multi-channel element as single constant: $other")
          }
        } {
          case i: Int     => Right(ConstantI(i))
          case d: Double  => Right(ConstantD(d))
          case n: Long    => Right(ConstantL(n))
          case b: Boolean => Right(ConstantI(if (b) 1 else 0))
          case other      => Left(s"Cannot convert attribute value to Float: $other")
        }

      case UnaryOp(op, a)  =>
        val af = resolve(a, builder)
        val op0 = UnaryOp.Op(op)
        af.right.map(op0.apply)

      case BinaryOp(op, a, b) =>
        val op0 = BinaryOp.Op(op)
        for {
          af <- resolve(a, builder).right
          bf <- resolve(b, builder).right
        } yield op0.apply(af, bf)
    }
  }

  /** An "untyped" action reference, i.e. without system type and transactions revealed */
  trait ActionRef {
    def key: String
    def execute(value: Any): Unit
  }

  /** An "untyped" output-setter reference */
  trait OutputRef {
    def key: String
    def complete(p: Output.Provider): Unit
  }
  /** An extended references as returned by the completed UGB. */
  trait OutputResult[S <: Sys[S]] extends OutputRef {
    def hasProvider: Boolean

    def updateValue()(implicit tx: S#Tx): Unit
  }

  final case class MissingIn(input: String) extends ControlThrowable

  // -----------------

  private final class BuilderImpl[S <: Sys[S]](f: FScape[S])(implicit tx: S#Tx, cursor: stm.Cursor[S],
                                                             workspace: WorkspaceHandle[S])
    extends UGenGraph.BuilderLike with UGenGraphBuilder { builder =>

    private var acceptedInputs: Set[String]                               = Set.empty
    private var outputMap     : Map[String, (Obj.Type, OutputResult[S])]  = Map.empty

    def requestAttribute(key: String): Option[Any] = {
      val res = f.attr.get(key) collect {
        case x: Expr[S, _]  => x.value
        case other          => other
      }
      if (res.isDefined) acceptedInputs += key
      res
    }

    def requestAction(key: String): Option[ActionRef] = {
      val res = f.attr.$[proc.Action](key).map { a =>
        new ActionRefImpl(key, tx.newHandle(f), tx.newHandle(a))
      }
      if (res.isDefined) acceptedInputs += key
      res
    }

    def requestOutput(key: String, tpe: Obj.Type): Option[OutputRef] = {
      val outOpt  = f.outputs.get(key)
      val res     = outOpt.collect {
        case out: OutputImpl[S] if out.valueType.typeID == tpe.typeID =>
          val ref = new OutputRefImpl(key, tx.newHandle(out))
          outputMap += key -> ((tpe, ref))
          ref
      }
      res
    }

    def tryBuild()(implicit ctrl: Control): State[S] =
      try {
        val iUGens = UGenGraph.indexUGens(ugens)
        new Complete[S] {
          private def calcStructure(): Long = {
            // val t1 = System.currentTimeMillis()
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

              val aux = ugen.aux
              if (aux.isEmpty) {
                out.writeShort(0)
              } else {
                out.writeShort(aux.size)
                aux.foreach(_.write(out))
              }

              idx += 1
            }

            val bytes = out.toByteArray
            val res = util.Arrays.hashCode(bytes) & 0x00000000FFFFFFFFL // XXX TODO use real 64-bit or 128-bit hash
            // val t2 = System.currentTimeMillis()
            // println(s"calcStructure took ${t2 - t1}ms")
            res
          }

          private def calcStream(): UGenGraph = {
            val rg = UGenGraph.buildStream(iUGens)
            UGenGraph(rg)
          }

          lazy val structure  : Long                                      = calcStructure()
          lazy val graph      : UGenGraph                                 = calcStream()
          val acceptedInputs  : Set[String]                               = builder.acceptedInputs
          val outputs         : Map[String, (Obj.Type, OutputResult[S])]  = builder.outputMap
        }
      } catch {
        case MissingIn(key) =>
          new Incomplete[S] {
            val rejectedInputs: Set[String]                               = Set(key)
            val acceptedInputs: Set[String]                               = builder.acceptedInputs
            val outputs       : Map[String, (Obj.Type, OutputResult[S])]  = builder.outputMap
          }
      }
  }

  private final class ActionRefImpl[S <: Sys[S]](val key: String,
                                                 fH: stm.Source[S#Tx, FScape[S]], aH: stm.Source[S#Tx, proc.Action[S]])
                                                (implicit cursor: stm.Cursor[S], workspace: WorkspaceHandle[S])
    extends ActionRef {

    def execute(value: Any): Unit = SoundProcesses.atomic[S, Unit] { implicit tx =>
      val f = fH()
      val a = aH()
      val u = proc.Action.Universe(self = a, workspace = workspace, invoker = Some(f), value = value)
      a.execute(u)
    }
  }

  private final class OutputRefImpl[S <: Sys[S]](val key: String,
                                                 outputH: stm.Source[S#Tx, OutputImpl[S]])
                                                (implicit cursor: stm.Cursor[S], workspace: WorkspaceHandle[S])
    extends OutputResult[S] {

    @volatile private[this] var provider: Output.Provider = _

    def complete(p: Output.Provider): Unit = provider = p

    def hasProvider: Boolean = provider != null

    def updateValue()(implicit tx: S#Tx): Unit /* Obj[S] */ = {
      if (provider == null) throw new IllegalStateException("Output was not provided")
      val value     = provider.mkValue
      val output    = outputH()
      output.value_=(Some(value))
    }
  }
}
trait UGenGraphBuilder extends UGenGraph.Builder {
  def requestAttribute(key: String): Option[Any]

  def requestAction   (key: String)               : Option[ActionRef]
  def requestOutput   (key: String, tpe: Obj.Type): Option[OutputRef]
}