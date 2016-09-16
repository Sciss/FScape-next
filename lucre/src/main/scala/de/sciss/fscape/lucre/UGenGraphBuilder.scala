/*
 *  UGenGraphBuilder.scala
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
package lucre

import de.sciss.fscape.graph.{BinaryOp, Constant, ConstantD, ConstantI, ConstantL, UnaryOp}
import de.sciss.fscape.lucre.UGenGraphBuilder.ActionRef
import de.sciss.fscape.lucre.graph.Attribute
import de.sciss.fscape.stream.Control
import de.sciss.lucre.expr.Expr
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc
import de.sciss.synth.proc.{SoundProcesses, WorkspaceHandle}

object UGenGraphBuilder {
  def get(b: UGenGraph.Builder): UGenGraphBuilder = b match {
    case ub: UGenGraphBuilder => ub
    case _ => sys.error("Out of context expansion")
  }

  def build[S <: Sys[S]](f: FScape[S], graph: Graph)(implicit tx: S#Tx, cursor: stm.Cursor[S],
                                                     workspace: WorkspaceHandle[S],
                                                     ctrl: Control): UGenGraph = {
    val b = new BuilderImpl(f)
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

  //  private def resolveInt(in: GE, builder: UGenGraphBuilder): Int = in match {
  //    case Constant(f) => f.toInt
  //    case a: Attribute => ...
  //    case UnaryOpUGen(op, a) =>
  //      // support a few ops directly without having to go back to float conversion
  //      op match {
  //        case UnaryOpUGen.Neg  => -resolveInt(a, builder)
  //        case UnaryOpUGen.Abs  => math.abs(resolveInt(a, builder))
  //        case _                => resolveFloat(in, builder).toInt
  //      }
  //    case BinaryOpUGen(op, a, b) =>
  //      // support a few ops directly without having to go back to float conversion
  //      op match {
  //        case BinaryOpUGen.Plus  => ...
  //        case _                  => resolveFloat(in, builder).toInt
  //      }
  //      resolveFloat(in, builder).toInt
  //  }

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

  // -----------------

  private final class BuilderImpl[S <: Sys[S]](f: FScape[S])(implicit tx: S#Tx, cursor: stm.Cursor[S],
                                                             workspace: WorkspaceHandle[S],
                                                             protected val ctrl: Control)
    extends UGenGraph.BuilderLike with UGenGraphBuilder {

    def requestAttribute(key: String): Option[Any] =
      f.attr.get(key) collect {
        case x : Expr[S, _] => x.value
        case other => other
      }

    def requestAction(key: String): Option[ActionRef] =
      f.attr.$[proc.Action](key).map { a =>
        new ActionRefImpl(tx.newHandle(f), tx.newHandle(a))
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
}
trait UGenGraphBuilder extends UGenGraph.Builder {
  def requestAttribute(key: String): Option[Any]

  def requestAction(key: String): Option[ActionRef]
}