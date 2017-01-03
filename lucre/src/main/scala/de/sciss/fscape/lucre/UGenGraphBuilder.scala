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

import de.sciss.fscape.graph.{BinaryOp, Constant, ConstantD, ConstantI, ConstantL, UnaryOp}
import de.sciss.fscape.lucre.FScape.Output
import de.sciss.fscape.lucre.UGenGraphBuilder.{ActionRef, OutputRef}
import de.sciss.fscape.lucre.graph.Attribute
import de.sciss.fscape.lucre.impl.OutputImpl
import de.sciss.fscape.stream.Control
import de.sciss.lucre.expr.Expr
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Obj, Sys}
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
    def outputs: Map[String, (Obj.Type, OutputRef)]
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
    def execute(value: Any): Unit
  }

  /** An "untyped" output-setter reference */
  trait OutputRef {
    def complete(p: Output.Provider): Unit
  }

  final case class MissingIn(input: String) extends ControlThrowable

  // -----------------

  private final class BuilderImpl[S <: Sys[S]](f: FScape[S])(implicit tx: S#Tx, cursor: stm.Cursor[S],
                                                             workspace: WorkspaceHandle[S],
                                                             protected val ctrl: Control)
    extends UGenGraph.BuilderLike with UGenGraphBuilder { builder =>

    private var acceptedInputs: Set[String]                         = Set.empty
    private var outputMap     : Map[String, (Obj.Type, OutputRef)]  = Map.empty

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
        new ActionRefImpl(tx.newHandle(f), tx.newHandle(a))
      }
      if (res.isDefined) acceptedInputs += key
      res
    }

    def requestOutput(key: String, tpe: Obj.Type): Option[OutputRef] = {
      val outOpt  = f.outputs.get(key)
      val res     = outOpt.collect {
        case out: OutputImpl[S] if out.valueType.typeID == tpe.typeID =>
          val ref = new OutputRefImpl(tx.newHandle(out))
          outputMap += key -> ((tpe, ref))
          ref
      }
      res
    }

    def tryBuild(): State[S] =
      try {
        val ug = build
        new Complete[S] {
          val graph           : UGenGraph                           = ug
          val acceptedInputs  : Set[String]                         = builder.acceptedInputs
          val outputs         : Map[String, (Obj.Type, OutputRef)]  = builder.outputMap
        }
      } catch {
        case MissingIn(key) =>
          new Incomplete[S] {
            val rejectedInputs: Set[String]                         = Set(key)
            val acceptedInputs: Set[String]                         = builder.acceptedInputs
            val outputs       : Map[String, (Obj.Type, OutputRef)]  = builder.outputMap
          }
      }
  }

  private final class ActionRefImpl[S <: Sys[S]](fH: stm.Source[S#Tx, FScape[S]], aH: stm.Source[S#Tx, proc.Action[S]])
                                                (implicit cursor: stm.Cursor[S], workspace: WorkspaceHandle[S])
    extends ActionRef {

    def execute(value: Any): Unit = SoundProcesses.atomic[S, Unit] { implicit tx =>
      val f = fH()
      val a = aH()
      val u = proc.Action.Universe(self = a, workspace = workspace, invoker = Some(f), value = value)
      a.execute(u)
    }
  }

  private final class OutputRefImpl[S <: Sys[S]](outputH: stm.Source[S#Tx, OutputImpl[S]])
                                                (implicit cursor: stm.Cursor[S], workspace: WorkspaceHandle[S])
    extends OutputRef {

    @volatile private[this] var provider: Output.Provider = _

    def complete(p: Output.Provider): Unit = provider = p

    def hasProvider: Boolean = provider != null

    def mkValue()(implicit tx: S#Tx): Obj[S] = {
      if (provider == null) throw new IllegalStateException("Output was not provided")
      provider.mkValue
    }
  }
}
trait UGenGraphBuilder extends UGenGraph.Builder {
  def requestAttribute(key: String): Option[Any]

  def requestAction   (key: String)               : Option[ActionRef]
  def requestOutput   (key: String, tpe: Obj.Type): Option[OutputRef]
}