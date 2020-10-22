/*
 *  FScape.scala
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

import de.sciss.fscape.lucre.impl.{FScapeRunnerImpl, OutputImpl, UGenGraphBuilderContextImpl, FScapeImpl => Impl}
import de.sciss.fscape.stream.Control
import de.sciss.lucre.{Disposable, Obj, Observable, Publisher, Txn, Workspace}
import de.sciss.serial.{DataInput, TFormat}
import de.sciss.synth.proc
import de.sciss.synth.proc.Code.{Example, Import}
import de.sciss.synth.proc.impl.CodeImpl
import de.sciss.synth.proc.{Gen, GenView, Runner, Universe}
import de.sciss.{fscape, model}

import scala.collection.immutable.{IndexedSeq => Vec, Seq => ISeq}
import scala.concurrent.Future
import scala.util.Try

object FScape extends Obj.Type {
  final val typeId = 0x1000B

  // ---- implementation forwards ----

  /** Registers this type and the graph object type.
    * You can use this call to register all FScape components.
    */
  override def init(): Unit = {
    super   .init()
    Output  .init()
    GraphObj.init()
    Code    .init()

    FScapeRunnerImpl.init()
  }

  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T] = Impl[T]

  def read[T <: Txn[T]](in: DataInput)(implicit tx: T): FScape[T] = Impl.read(in)

  implicit def format[T <: Txn[T]]: TFormat[T, FScape[T]] = Impl.format[T]

  // ---- event types ----

  /** An update is a sequence of changes */
  final case class Update[T <: Txn[T]](proc: FScape[T], changes: Vec[Change[T]])

  /** A change is either a state change, or a scan or a grapheme change */
  sealed trait Change[T <: Txn[T]]

  final case class GraphChange[T <: Txn[T]](change: model.Change[Graph]) extends Change[T]

  /** An output change is either adding or removing an output */
  sealed trait OutputsChange[T <: Txn[T]] extends Change[T] {
    def output: Output[T]
  }

  final case class OutputAdded  [T <: Txn[T]](output: Output[T]) extends OutputsChange[T]
  final case class OutputRemoved[T <: Txn[T]](output: Output[T]) extends OutputsChange[T]

  /** Source code of the graph function. */
  final val attrSource = "graph-source"

  override def readIdentifiedObj[T <: Txn[T]](in: DataInput)(implicit tx: T): Obj[T] =
    Impl.readIdentifiedObj(in)

  // ----

  object Rendering {
    type State                                    = GenView.State
    val  Completed: GenView.Completed       .type = GenView.Completed
    val  Running  : GenView.Running         .type = GenView.Running
    type Running                                  = GenView.Running

    val  Cancelled: fscape.stream.Cancelled .type = fscape.stream.Cancelled
    type Cancelled                                = fscape.stream.Cancelled

    /** Creates a view with the default `UGenGraphBuilder.Context`. */
    def apply[T <: Txn[T]](peer: FScape[T], config: Control.Config, attr: Runner.Attr[T] = Runner.emptyAttr[T])
                          (implicit tx: T, universe: Universe[T]): Rendering[T] = {
      val ugbCtx = new UGenGraphBuilderContextImpl.Default(peer, attr = attr)
      impl.RenderingImpl(peer, ugbCtx, config, force = true)
    }
  }
  trait Rendering[T <: Txn[T]] extends Observable[T, Rendering.State] with Disposable[T] {
    def state(implicit tx: T): Rendering.State

    def result(implicit tx: T): Option[Try[Unit]]

    def outputResult(output: OutputGenView[T])(implicit tx: T): Option[Try[Obj[T]]]

    def control: Control

    /** Like `react` but invokes the function immediately with the current state. */
    def reactNow(fun: T => Rendering.State => Unit)(implicit tx: T): Disposable[T]

    def cancel()(implicit tx: T): Unit
  }

  // ---- Code ----

  object Code extends proc.Code.Type {
    final val id = 4

    final val prefix    = "FScape"
    final val humanName = "FScape Graph"

    type Repr = Code

    override def examples: ISeq[Example] = List(
      Example("Plot Sine", 'p',
        """val sr  = 44100.0
          |val sig = SinOsc(440 / sr)
          |Plot1D(sig, 500)
          |""".stripMargin
      )
    )

    def docBaseSymbol: String = "de.sciss.fscape.graph"

    private[this] lazy val _init: Unit = {
      proc.Code.addType(this)
      import Import._
      proc.Code.registerImports(id, Vec(
        // doesn't work:
//        "Predef.{any2stringadd => _, _}", // cf. http://stackoverflow.com/questions/7634015/
        Import("de.sciss.numbers.Implicits", All),
//        "de.sciss.fscape.GE",
        Import("de.sciss.fscape", All),
        Import("de.sciss.fscape.graph", List(Ignore("AudioFileIn"), Ignore("AudioFileOut"), Ignore("ImageFileIn"),
          Ignore("ImageFileOut"), Ignore("ImageFileSeqIn"), Ignore("ImageFileSeqOut"), Wildcard)),
        Import("de.sciss.fscape.lucre.graph", All),
        Import("de.sciss.fscape.lucre.graph.Ops", All)
      ))
    }

    // override because we need register imports
    override def init(): Unit = _init

    def mkCode(source: String): Repr = Code(source)
  }
  final case class Code(source: String) extends proc.Code {
    type In     = Unit
    type Out    = fscape.Graph

    def tpe: proc.Code.Type = Code

    def compileBody()(implicit compiler: proc.Code.Compiler): Future[Unit] = {
      import scala.reflect.runtime.universe._
      CodeImpl.compileBody[In, Out, Unit, Code](this, typeTag[Unit])
    }

    def execute(in: In)(implicit compiler: proc.Code.Compiler): Out =
      Graph {
        import scala.reflect.runtime.universe._
        CodeImpl.compileThunk[Unit](this, typeTag[Unit], execute = true)
      }

    def prelude : String = "object Main {\n"

    def postlude: String = "\n}\n"

    def updateSource(newText: String): Code = copy(source = newText)
  }

  object Output extends Obj.Type {
    final val typeId = 0x1000D

    def read[T <: Txn[T]](in: DataInput)(implicit tx: T): Output[T] = OutputImpl.read(in)

    implicit def format[T <: Txn[T]]: TFormat[T, Output[T]] = OutputImpl.format

    override def readIdentifiedObj[T <: Txn[T]](in: DataInput)(implicit tx: T): Obj[T] =
      OutputImpl.readIdentifiedObj(in)

    trait Reader {
      def key: String
      def tpe: Obj.Type

      def readOutputValue(in: DataInput): Any

      def readOutput[T <: Txn[T]](in: DataInput)(implicit tx: T, workspace: Workspace[T]): Obj[T]
    }

    trait Writer extends de.sciss.serial.Writable {
      def outputValue: Any
    }
  }
  trait Output[T <: Txn[T]] extends Gen[T] /* with Publisher[T, Output.Update[T]] */ {
    def fscape: FScape[T]
    def key   : String
  }

  trait Outputs[T <: Txn[T]] {
    def get(key: String)(implicit tx: T): Option[Output[T]]

    def keys(implicit tx: T): Set[String]

    def iterator(implicit tx: T): Iterator[Output[T]]

    /** Adds a new output by the given key and type.
      * If an output by that name and type already exists, the old output is returned.
      * If the type differs, removes the old output and creates a new one.
      */
    def add   (key: String, tpe: Obj.Type)(implicit tx: T): Output[T]

    def remove(key: String)(implicit tx: T): Boolean
  }

  def genViewFactory(config: Control.Config = defaultConfig): GenView.Factory = Impl.genViewFactory(config)

  @volatile
  private[this] var _defaultConfig: Control.Config = _

  private lazy val _lazyDefaultConfig: Control.Config = {
    val b             = Control.Config()
    b.useAsync        = false
    b.terminateActors = false
    // b.actorTxntem = b.actorTxntem
    b
  }

  /** There is currently a problem with building `Config().build` multiple times,
    * in that we create new actor systems and materializers that will not be shut down,
    * unless an actual rendering is performed. As a work around, use this single
    * instance which will reuse one and the same actor system.
    */
  def defaultConfig: Control.Config = {
    if (_defaultConfig == null) _defaultConfig = _lazyDefaultConfig
    _defaultConfig
  }

  def defaultConfig_=(value: Control.Config): Unit =
    _defaultConfig = value

  type Config = Control.Config
  val  Config = Control.Config
}

/** The `FScape` trait is the basic entity representing a sound process. */
trait FScape[T <: Txn[T]] extends Obj[T] with Publisher[T, FScape.Update[T]] {
  /** The variable synth graph function of the process. */
  def graph: GraphObj.Var[T]

  def outputs: FScape.Outputs[T]

  def run(config: Control.Config = FScape.defaultConfig, attr: Runner.Attr[T] = Runner.emptyAttr[T])
         (implicit tx: T, universe: Universe[T]): FScape.Rendering[T]
}