/*
 *  UGenGraphBuilder.scala
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
package lucre

import de.sciss.file.File
import de.sciss.fscape.graph.impl.GESeq
import de.sciss.fscape.graph.{ArithmSeq, BinaryOp, Constant, ConstantD, ConstantI, ConstantL, GeomSeq, UnaryOp}
import de.sciss.fscape.lucre.FScape.Output
import de.sciss.fscape.lucre.UGenGraphBuilder.OutputRef
import de.sciss.fscape.lucre.graph.Attribute
import de.sciss.fscape.lucre.impl.{AbstractOutputRef, AbstractUGenGraphBuilder, OutputImpl}
import de.sciss.fscape.stream.Control
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Sys, WorkspaceHandle}
import de.sciss.serial.DataInput
import de.sciss.synth.UGenSource.Vec

import scala.collection.breakOut
import scala.util.control.ControlThrowable

object UGenGraphBuilder {
  def get(b: UGenGraph.Builder): UGenGraphBuilder = b match {
    case ub: UGenGraphBuilder => ub
    case _ => sys.error("Out of context expansion")
  }

  def build[S <: Sys[S]](context: Context[S], f: FScape[S])(implicit tx: S#Tx, cursor: stm.Cursor[S],
                                                            workspace: WorkspaceHandle[S],
                                                            ctrl: Control): State[S] = {
    val b = new BuilderImpl(context, f)
    val g = f.graph.value
    b.tryBuild(g)
  }

//  def build[S <: Sys[S]](context: Context[S], g: Graph)(implicit tx: S#Tx, cursor: stm.Cursor[S],
//                                                        workspace: WorkspaceHandle[S],
//                                                        ctrl: Control): State[S] =
//    buildOpt[S](context, None, g)
//
//  private def buildOpt[S <: Sys[S]](context: Context[S], fOpt: Option[FScape[S]], g: Graph)
//                                   (implicit tx: S#Tx, workspace: WorkspaceHandle[S],
//                                    ctrl: Control): State[S] = {
//    val b = new BuilderImpl(context, fOpt)
//    b.tryBuild(g)
//  }

  /** A pure marker trait to rule out some type errors. */
  trait Key
  /** A scalar value found in the attribute map. */
  final case class AttributeKey(name: String) extends Key

  /** A pure marker trait to rule out some type errors. */
  trait Value {
//    def async: Boolean
  }

  case object Unit extends Value
  type Unit = Unit.type

  object Input {

    object Attribute {
      final case class Value(peer: Option[Any]) extends UGenGraphBuilder.Value {
//        def async = false
        override def productPrefix = "Input.Attribute.Value"
      }
    }
    /** Specifies access to a an attribute's value at build time.
      *
      * @param name   name (key) of the attribute
      */
    final case class Attribute(name: String) extends Input {
      type Key    = AttributeKey
      type Value  = Attribute.Value

      def key = AttributeKey(name)

      override def productPrefix = "Input.Attribute"
    }

    object Action {
//      case object Value extends UGenGraphBuilder.Value {
//        def async = false
//        override def productPrefix = "Input.Action.Value"
//      }
      /** An "untyped" action reference, i.e. without system type and transactions revealed */
      trait Value extends UGenGraphBuilder.Value {
        def key: String
        def execute(value: Any): scala.Unit
      }
    }
    /** Specifies access to an action.
      *
      * @param name   name (key) of the attribute referring to an action
      */
    final case class Action(name: String) extends Input {
      type Key    = AttributeKey
      type Value  = Action.Value // .type

      def key = AttributeKey(name)

      override def productPrefix = "Input.Action"
    }
  }
  trait Input {
    type Key   <: UGenGraphBuilder.Key
    type Value <: UGenGraphBuilder.Value

    def key: Key
  }

  trait Context[S <: Sys[S]] {
//    def server: Server

    def requestInput[Res](req: UGenGraphBuilder.Input { type Value = Res }, io: IO[S] with UGenGraphBuilder)
                         (implicit tx: S#Tx): Res
  }

  trait IO[S <: Sys[S]] {
    // def acceptedInputs: Set[String]
    def acceptedInputs: Map[Key, Map[Input, Input#Value]]

    /** Current set of used outputs (scan keys to number of channels).
      * This is guaranteed to only grow during incremental building, never shrink.
      */
    def outputs: List[OutputResult[S]]
  }

  sealed trait State[S <: Sys[S]] extends IO[S] {
    def rejectedInputs: Set[String]

    def isComplete: Boolean

    override def toString: String = {
      val acceptedS = {
        val keys: List[String] = acceptedInputs.keys.map(_.toString)(breakOut)
        keys.sorted.mkString(s"accepted: [", ", ", "], ")
      }
      val rejectedS = if (isComplete) "" else {
        val keys: List[String] = rejectedInputs.map(_.toString)(breakOut)
        keys.sorted.mkString(s"rejected: [", ", ", "], ")
      }
      val outputsS = {
        val keys = outputs.map(_.key).sorted
        keys.mkString(s"outputs: [", ", ", "]")
      }
      val prefix = if (isComplete) "Complete" else "Incomplete"
      s"$prefix($acceptedS$rejectedS$outputsS)"
    }
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

  def canResolve(in: GE): Either[String, scala.Unit] =
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

  def canResolveSeq(in: GE): Either[String, scala.Unit] =
    in match {
      case GESeq(elems) =>
        def loop(seq: Vec[GE]): Either[String, scala.Unit] =
          seq match {
            case head +: tail =>
              canResolve(head) match {
                case Right(_) => loop(tail)
                case not => not
              }

            case _ => Right(())
          }

        loop(elems)

      case GeomSeq(start, grow, length) =>
        for {
          _ <- canResolve(start ).right
          _ <- canResolve(grow  ).right
          _ <- canResolve(length).right
        } yield ()

      case ArithmSeq(start, step, length) =>
        for {
          _ <- canResolve(start ).right
          _ <- canResolve(step  ).right
          _ <- canResolve(length).right
        } yield ()

      case _  => canResolve(in)
    }

  def resolve(in: GE, builder: UGenGraphBuilder): Either[String, Constant] =
    in match {
      case c: Constant => Right(c)
      case a: Attribute =>
        builder.requestInput(Input.Attribute(a.key)).peer.fold[Either[String, Constant]] {
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

  def resolveSeq(in: GE, builder: UGenGraphBuilder): Either[String, Vec[Constant]] =
    in match {
      case GESeq(elems) =>
        def loop(seq: Vec[GE], out: Vec[Constant]): Either[String, Vec[Constant]] =
          seq match {
            case head +: tail =>
              resolve(head, builder) match {
                case Right(c) => loop(tail, out :+ c)
                case Left(x)  => Left(x)
              }

            case _ => Right(out)
          }

        loop(elems, Vector.empty)

      case GeomSeq(start, grow, length) =>
        for {
          startC  <- resolve(start , builder).right
          growC   <- resolve(grow  , builder).right
          lengthC <- resolve(length, builder).right
        } yield {
          val b       = Vector.newBuilder[Constant]
          val len     = lengthC.intValue
          b.sizeHint(lengthC.intValue)
          val isLong  = (startC.isInt || startC.isLong) && (growC.isInt || growC.isLong)
          var i = 0
          if (isLong) {
            var n = startC.longValue
            val g = growC .longValue
            while (i < len) {
              b += n
              n *= g
              i += 1
            }
          } else {
            var n = startC.doubleValue
            val g = growC .doubleValue
            while (i < len) {
              b += n
              n *= g
              i += 1
            }
          }
          b.result()
        }

      case ArithmSeq(start, step, length) =>
        for {
          startC  <- resolve(start , builder).right
          stepC   <- resolve(step  , builder).right
          lengthC <- resolve(length, builder).right
        } yield {
          val b       = Vector.newBuilder[Constant]
          val len     = lengthC.intValue
          b.sizeHint(lengthC.intValue)
          val isLong  = (startC.isInt || startC.isLong) && (stepC.isInt || stepC.isLong)
          var i = 0
          if (isLong) {
            var n = startC.longValue
            val g = stepC .longValue
            while (i < len) {
              b += n
              n += g
              i += 1
            }
          } else {
            var n = startC.doubleValue
            val g = stepC .doubleValue
            while (i < len) {
              b += n
              n += g
              i += 1
            }
          }
          b.result()
        }

      case _  => resolve(in, builder).right.map(Vector(_))
    }

    /** An "untyped" output-setter reference */
  trait OutputRef {
    /** The key in the `FScape` objects `outputs` dictionary. */
    def key: String

    /** Requests the stream control to create and memorize a
      * file that will be written during the rendering and should
      * be added as a resource associated with this key/reference.
      */
    def createCacheFile(): File

    /** To be called by the stream node upon completion. Signals that
      * the node has completed and the passed `Output.Provider` is ready
      * to receive the `mkValue` call.
      */
    def complete(w: Output.Writer): scala.Unit
  }
  /** An extended references as returned by the completed UGB. */
  trait OutputResult[S <: Sys[S]] extends OutputRef {
    def reader: Output.Reader

    /** Returns `true` after `complete` has been called, or `false` before.
      * `true` signals that `updateValue` may now be called.
      */
    def hasWriter: Boolean

    def writer: Output.Writer

    /** Issues the underlying `Output` implementation to replace its
      * value with the new updated value.
      */
    def updateValue(in: DataInput)(implicit tx: S#Tx): scala.Unit

    /** A list of cache files created during rendering for this key,
      * created via `createCacheFile()`, or `Nil` if this output did not
      * produce any additional resource files.
      */
    def cacheFiles: List[File]
  }

  final case class MissingIn(input: String) extends ControlThrowable

  // -----------------

  private final class BuilderImpl[S <: Sys[S]](protected val context: Context[S], fscape: FScape[S])
                                              (implicit tx: S#Tx, // cursor: stm.Cursor[S],
                                               workspace: WorkspaceHandle[S])
    extends AbstractUGenGraphBuilder[S] { builder =>

    protected def requestOutputImpl(reader: Output.Reader): Option[OutputResult[S]] = {
      val outOpt = fscape.outputs.get(reader.key)
      outOpt.collect {
        case out: OutputImpl[S] if out.valueType.typeId == reader.tpe.typeId =>
          new OutputRefImpl(reader, tx.newHandle(out))
      }
    }
  }

  private final class OutputRefImpl[S <: Sys[S]](val reader: Output.Reader,
                                                 outputH: stm.Source[S#Tx, OutputImpl[S]])
                                                (implicit workspace: WorkspaceHandle[S])
    extends AbstractOutputRef[S] {

    def updateValue(in: DataInput)(implicit tx: S#Tx): scala.Unit = {
      val value     = reader.readOutput[S](in)
      val output    = outputH()
      output.value_=(Some(value))
    }
  }
}
trait UGenGraphBuilder extends UGenGraph.Builder {
//  def requestAttribute(key: String): Option[Any]
//
//  def requestAction   (key: String)          : Option[ActionRef]

  def requestInput(input: UGenGraphBuilder.Input): input.Value

  def requestOutput(reader: Output.Reader): Option[OutputRef]
}