/*
 *  UGenGraphBuilder.scala
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
package lucre

import de.sciss.fscape.graph.impl.GESeq
import de.sciss.fscape.graph.{ArithmSeq, BinaryOp, Constant, ConstantD, ConstantI, ConstantL, GeomSeq, UnaryOp}
import de.sciss.fscape.lucre.UGenGraphBuilder.OutputRef
import de.sciss.fscape.lucre.graph.Attribute
import de.sciss.fscape.lucre.impl.{AbstractOutputRef, AbstractUGenGraphBuilder}
import de.sciss.fscape.stream.Control
import de.sciss.lucre.expr.graph.{Const => ExConst, Var => ExVar}
import de.sciss.lucre.{Artifact, Source, Txn, Workspace}
import de.sciss.serial.DataInput
import de.sciss.synth.UGenSource.Vec
import de.sciss.proc.FScape.Output
import de.sciss.proc.impl.FScapeOutputImpl
import de.sciss.proc.{FScape, Runner}

import scala.annotation.tailrec
import scala.util.control.ControlThrowable

object UGenGraphBuilder /*extends UGenGraphBuilderPlatform*/ {
  def get(b: UGenGraph.Builder): UGenGraphBuilder = b match {
    case ub: UGenGraphBuilder => ub
    case _ => sys.error("Out of context expansion")
  }

  def build[T <: Txn[T]](context: Context[T], f: FScape[T])(implicit tx: T,
                                                            workspace: Workspace[T],
                                                            ctrl: Control): State[T] = {
    val b = new BuilderImpl(context, f)
    val g = f.graph.value
    b.tryBuild(g)
  }

//  def build[T <: Txn[T]](context: Context[T], g: Graph)(implicit tx: T, cursor: stm.Cursor[T],
//                                                        workspace: Workspace[T],
//                                                        ctrl: Control): State[T] =
//    buildOpt[T](context, None, g)
//
//  private def buildOpt[T <: Txn[T]](context: Context[T], fOpt: Option[FScape[T]], g: Graph)
//                                   (implicit tx: T, workspace: Workspace[T],
//                                    ctrl: Control): State[T] = {
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
        def execute(value: Option[Any]): scala.Unit
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

  trait Context[T <: Txn[T]] {

    def attr: Runner.Attr[T]

    def requestInput[Res](req: UGenGraphBuilder.Input { type Value = Res }, io: IO[T] with UGenGraphBuilder)
                         (implicit tx: T): Res
  }

  trait IO[T <: Txn[T]] {
    // def acceptedInputs: Set[String]
    def acceptedInputs: Map[Key, Map[Input, Input#Value]]

    /** Current set of used outputs (scan keys to number of channels).
      * This is guaranteed to only grow during incremental building, never shrink.
      */
    def outputs: List[OutputResult[T]]
  }

  sealed trait State[T <: Txn[T]] extends IO[T] {
    def rejectedInputs: Set[String]

    def isComplete: Boolean

    override def toString: String = {
      val acceptedS = {
        val keys: List[String] = acceptedInputs.keysIterator.map(_.toString).toList
        keys.sorted.mkString(s"accepted: [", ", ", "], ")
      }
      val rejectedS = if (isComplete) "" else {
        val keys: List[String] = rejectedInputs.iterator.map(_.toString).toList
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

  trait Incomplete[T <: Txn[T]] extends State[T] {
    final def isComplete = false
  }

  trait Complete[T <: Txn[T]] extends State[T] {
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
        @tailrec
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
        @tailrec
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
  trait OutputRef /*extends OutputRefPlatform*/ {
    /** The key in the `FScape` objects `outputs` dictionary. */
    def key: String

    /** To be called by the stream node upon completion. Signals that
      * the node has completed and the passed `Output.Provider` is ready
      * to receive the `mkValue` call.
      */
    def complete(w: Output.Writer): scala.Unit

    /** Requests the stream control to create and memorize a
      * file that will be written during the rendering and should
      * be added as a resource associated with this key/reference.
      */
    def createCacheFile(): Artifact.Value
  }
  /** An extended references as returned by the completed UGB. */
  trait OutputResult[T <: Txn[T]] extends OutputRef /*with OutputResultPlatform*/ {
    def reader: Output.Reader

    /** Returns `true` after `complete` has been called, or `false` before.
      * `true` signals that `updateValue` may now be called.
      */
    def hasWriter: Boolean

    def writer: Output.Writer

    /** Issues the underlying `Output` implementation to replace its
      * value with the new updated value.
      */
    def updateValue(in: DataInput)(implicit tx: T): scala.Unit

    /** A list of cache files created during rendering for this key,
      * created via `createCacheFile()`, or `Nil` if this output did not
      * produce any additional resource files.
      */
    def cacheFiles: List[Artifact.Value]
  }

  final case class MissingIn(input: String) extends ControlThrowable {
    override def getMessage: String = input

    override def toString: String = s"UGenGraphBuilder.MissingIn: $input"
  }

  // -----------------

  private final class BuilderImpl[T <: Txn[T]](protected val context: Context[T], fscape: FScape[T])
                                              (implicit tx: T, // cursor: stm.Cursor[T],
                                               workspace: Workspace[T])
    extends AbstractUGenGraphBuilder[T] { builder =>

    // we first check for a named output, and then try to fallback to
    // an `expr.graph.Var` provided attr argument.
    protected def requestOutputImpl(reader: Output.Reader): Option[OutputResult[T]] = {
      val key = reader.key
      val outOpt = fscape.outputs.get(key)
      outOpt match {
        case Some(out: FScapeOutputImpl[T]) =>
          if (out.valueType.typeId == reader.tpe.typeId) {
            val res = new ObjOutputRefImpl(reader, tx.newHandle(out))
            Some(res)
          } else {
            None
          }
        case _ =>
          val attrOpt = context.attr.get(key)
          attrOpt match {
            case Some(ex: ExVar.Expanded[T, _]) =>
              val res = new CtxOutputRefImpl(reader, ex)
              Some(res)
            case _ =>
              None
          }
      }
    }
  }

  private final class CtxOutputRefImpl[T <: Txn[T], A](val reader: Output.Reader,
                                                       vr: ExVar.Expanded[T, A])
//                                                      (implicit workspace: Workspace[T])
    extends AbstractOutputRef[T] {

    def updateValue(in: DataInput)(implicit tx: T): scala.Unit = {
      val value = reader.readOutputValue(in)
      vr.fromAny.fromAny(value).foreach { valueT =>
        vr.update(new ExConst.Expanded(valueT))
      }
    }
  }

  private final class ObjOutputRefImpl[T <: Txn[T]](val reader: Output.Reader,
                                                 outputH: Source[T, FScapeOutputImpl[T]])
                                                (implicit workspace: Workspace[T])
    extends AbstractOutputRef[T] {

    def updateValue(in: DataInput)(implicit tx: T): scala.Unit = {
      val obj     = reader.readOutput[T](in)
      val output  = outputH()
      output.value_=(Some(obj))
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