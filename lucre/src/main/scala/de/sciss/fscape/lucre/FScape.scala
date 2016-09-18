/*
 *  FScape.scala
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

import de.sciss.fscape.lucre.impl.{FScapeImpl => Impl}
import de.sciss.fscape.stream.Control
import de.sciss.lucre.event.{Observable, Publisher}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj, Sys}
import de.sciss.serial.{DataInput, Serializer}
import de.sciss.synth.proc
import de.sciss.synth.proc.WorkspaceHandle
import de.sciss.synth.proc.impl.CodeImpl
import de.sciss.{fscape, model}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.Future

object FScape extends Obj.Type {
  final val typeID = 0x1000B

  // ---- implementation forwards ----

  /** Registers this type and the graph object type.
    * You can use this call to register all FScape components.
    */
  override def init(): Unit = {
    super   .init()
    GraphObj.init()
    Code    .init()
  }

  def apply[S <: Sys[S]](implicit tx: S#Tx): FScape[S] = Impl[S]

  def read[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): FScape[S] = Impl.read(in, access)

  implicit def serializer[S <: Sys[S]]: Serializer[S#Tx, S#Acc, FScape[S]] = Impl.serializer[S]

  // ---- event types ----

  /** An update is a sequence of changes */
  final case class Update[S <: Sys[S]](proc: FScape[S], changes: Vec[Change[S]])

  /** A change is either a state change, or a scan or a grapheme change */
  sealed trait Change[S <: Sys[S]]

  final case class GraphChange[S <: Sys[S]](change: model.Change[Graph]) extends Change[S]

  /** Source code of the graph function. */
  final val attrSource = "graph-source"

  override def readIdentifiedObj[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): Obj[S] =
    Impl.readIdentifiedObj(in, access)

  // ----

  object Rendering {
    sealed trait State {
      def isComplete: Boolean
    }
    case object Success extends State {
      def isComplete = true
    }
    /** Rendering either failed or was aborted.
      * In the case of abortion, the throwable is
      * of type `Cancelled`.
      */
    final case class Failure (ex : Throwable) extends State {
      def isComplete = true
    }
//    final case class Progress(amount: Double) extends State {
//      override def toString = s"$productPrefix($toInt%)"
//
//      def isComplete = false
//
//      /** Returns an integer progress percentage between 0 and 100 */
//      def toInt = (amount * 100).toInt
//    }
    case object Running extends State {
      def isComplete = false
    }

    val  Cancelled = fscape.stream.Cancelled
    type Cancelled = fscape.stream.Cancelled
  }
  trait Rendering[S <: Sys[S]] extends Observable[S#Tx, Rendering.State] with Disposable[S#Tx] {
    def state(implicit tx: S#Tx): Rendering.State

    def control: Control

    /** Like `react` but invokes the function immediately with the current state. */
    def reactNow(fun: S#Tx => Rendering.State => Unit)(implicit tx: S#Tx): Disposable[S#Tx]

    def cancel()(implicit tx: S#Tx): Unit
  }

  // ---- Code ----

  object Code extends proc.Code.Type {
    final val id    = 4
    final val name  = "FScape Graph"
    type Repr = Code

    private[this] lazy val _init: Unit = {
      proc.Code.addType(this)
      proc.Code.registerImports(id, Vec(
        // doesn't work:
//        "Predef.{any2stringadd => _, _}", // cf. http://stackoverflow.com/questions/7634015/
        "de.sciss.numbers.Implicits._",
        "de.sciss.fscape.GE",
        "de.sciss.fscape.graph.{AudioFileIn => _, AudioFileOut => _, _}",
        "de.sciss.fscape.lucre.graph._",
        "de.sciss.fscape.lucre.graph.Ops._"
      ))
      proc.Code.registerImports(proc.Code.Action.id, Vec(
        "de.sciss.fscape.lucre.FScape"
      ))
    }

    // override because we need register imports
    override def init(): Unit = _init

    def mkCode(source: String): Repr = Code(source)
  }
  final case class Code(source: String) extends proc.Code {
    type In     = Unit
    type Out    = fscape.Graph
    def id      = Code.id

    def compileBody()(implicit compiler: proc.Code.Compiler): Future[Unit] = {
      import Impl.CodeWrapper
      CodeImpl.compileBody[In, Out, Code](this)
    }

    def execute(in: In)(implicit compiler: proc.Code.Compiler): Out = {
      import Impl.CodeWrapper
      CodeImpl.execute[In, Out, Code](this, in)
    }

    def contextName = Code.name

    def updateSource(newText: String) = copy(source = newText)
  }
}

/** The `FScape` trait is the basic entity representing a sound process. */
trait FScape[S <: Sys[S]] extends Obj[S] with Publisher[S, FScape.Update[S]] {
  /** The variable synth graph function of the process. */
  def graph: GraphObj.Var[S]

  def run(config: Control.Config = Control.Config())
         (implicit tx: S#Tx, cursor: stm.Cursor[S], workspace: WorkspaceHandle[S]): FScape.Rendering[S]
}