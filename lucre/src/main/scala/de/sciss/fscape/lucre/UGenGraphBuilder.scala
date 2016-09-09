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
import de.sciss.fscape.lucre.graph.Attribute
import de.sciss.lucre.expr.Expr
import de.sciss.lucre.stm.Sys

object UGenGraphBuilder {
  def get(b: UGenGraph.Builder): UGenGraphBuilder = b match {
    case ub: UGenGraphBuilder => ub
    case _ => sys.error("Out of context expansion")
  }

  def build[S <: Sys[S]](f: FScape[S], graph: Graph)(implicit tx: S#Tx, ctrl: stream.Control): UGenGraph = {
    val b = new BuilderImpl(f)
    graph.sources.foreach { source =>
      source.force(b)
    }
    b.build
  }

  // ---- resolve ----

  def canResolve(in: GE): Either[String, Unit] = {
    in match {
      case _: Constant            => Right(())
      case _: Attribute           => Right(())
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
        af.right.map(op.apply)

      case BinaryOp(op, a, b) =>
        for {
          af <- resolve(a, builder).right
          bf <- resolve(b, builder).right
        } yield op.apply(af, bf)
    }
  }

  // -----------------

  private final class BuilderImpl[S <: Sys[S]](f: FScape[S])(implicit tx: S#Tx, protected val ctrl: stream.Control)
    extends UGenGraph.BuilderLike with UGenGraphBuilder {

    def requestAttribute(key: String): Option[Any] =
      f.attr.get(key) collect {
        case x : Expr[S, _] => x.value
        case other => other
      }
  }
}
trait UGenGraphBuilder extends UGenGraph.Builder {
  def requestAttribute(key: String): Option[Any]
}