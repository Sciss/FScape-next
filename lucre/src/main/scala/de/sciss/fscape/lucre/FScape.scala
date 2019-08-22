/*
 *  FScape.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.lucre.event.{Observable, Publisher}
import de.sciss.lucre.stm.{Disposable, Obj, Sys, Workspace}
import de.sciss.serial.{DataInput, Serializer}
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

  def apply[S <: Sys[S]]()(implicit tx: S#Tx): FScape[S] = Impl[S]

  def read[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): FScape[S] = Impl.read(in, access)

  implicit def serializer[S <: Sys[S]]: Serializer[S#Tx, S#Acc, FScape[S]] = Impl.serializer[S]

  // ---- event types ----

  /** An update is a sequence of changes */
  final case class Update[S <: Sys[S]](proc: FScape[S], changes: Vec[Change[S]])

  /** A change is either a state change, or a scan or a grapheme change */
  sealed trait Change[S <: Sys[S]]

  final case class GraphChange[S <: Sys[S]](change: model.Change[Graph]) extends Change[S]

  /** An output change is either adding or removing an output */
  sealed trait OutputsChange[S <: Sys[S]] extends Change[S] {
    def output: Output[S]
  }

  final case class OutputAdded  [S <: Sys[S]](output: Output[S]) extends OutputsChange[S]
  final case class OutputRemoved[S <: Sys[S]](output: Output[S]) extends OutputsChange[S]

  /** Source code of the graph function. */
  final val attrSource = "graph-source"

  override def readIdentifiedObj[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): Obj[S] =
    Impl.readIdentifiedObj(in, access)

  // ----

  object Rendering {
    type State                                    = GenView.State
    val  Completed: GenView.Completed       .type = GenView.Completed
    val  Running  : GenView.Running         .type = GenView.Running
    type Running                                  = GenView.Running

    val  Cancelled: fscape.stream.Cancelled .type = fscape.stream.Cancelled
    type Cancelled                                = fscape.stream.Cancelled

    /** Creates a view with the default `UGenGraphBuilder.Context`. */
    def apply[S <: Sys[S]](peer: FScape[S], config: Control.Config, attr: Runner.Attr[S] = Runner.emptyAttr[S])
                          (implicit tx: S#Tx, universe: Universe[S]): Rendering[S] = {
      val ugbCtx = new UGenGraphBuilderContextImpl.Default(peer, attr = attr)
      impl.RenderingImpl(peer, ugbCtx, config, force = true)
    }
  }
  trait Rendering[S <: Sys[S]] extends Observable[S#Tx, Rendering.State] with Disposable[S#Tx] {
    def state(implicit tx: S#Tx): Rendering.State

    def result(implicit tx: S#Tx): Option[Try[Unit]]

    def outputResult(output: OutputGenView[S])(implicit tx: S#Tx): Option[Try[Obj[S]]]

    def control: Control

    /** Like `react` but invokes the function immediately with the current state. */
    def reactNow(fun: S#Tx => Rendering.State => Unit)(implicit tx: S#Tx): Disposable[S#Tx]

    def cancel()(implicit tx: S#Tx): Unit
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

    def read[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): Output[S] = OutputImpl.read (in, access)

    implicit def serializer[S <: Sys[S]]: Serializer[S#Tx, S#Acc, Output[S]] = OutputImpl.serializer

    override def readIdentifiedObj[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): Obj[S] =
      OutputImpl.readIdentifiedObj(in, access)

    trait Reader {
      def key: String
      def tpe: Obj.Type

      def readOutput[S <: Sys[S]](in: DataInput)(implicit tx: S#Tx, workspace: Workspace[S]): Obj[S]
    }

    type Writer = de.sciss.serial.Writable
  }
  trait Output[S <: Sys[S]] extends Gen[S] /* with Publisher[S, Output.Update[S]] */ {
    def fscape: FScape[S]
    def key   : String
  }

  trait Outputs[S <: Sys[S]] {
    def get(key: String)(implicit tx: S#Tx): Option[Output[S]]

    def keys(implicit tx: S#Tx): Set[String]

    def iterator(implicit tx: S#Tx): Iterator[Output[S]]

    /** Adds a new output by the given key and type.
      * If an output by that name and type already exists, the old output is returned.
      * If the type differs, removes the old output and creates a new one.
      */
    def add   (key: String, tpe: Obj.Type)(implicit tx: S#Tx): Output[S]

    def remove(key: String)(implicit tx: S#Tx): Boolean
  }

  def genViewFactory(config: Control.Config = defaultConfig): GenView.Factory = Impl.genViewFactory(config)

  @volatile
  private[this] var _defaultConfig: Control.Config = _

  private lazy val _lazyDefaultConfig: Control.Config = {
    val b             = Control.Config()
    b.useAsync        = false
    b.terminateActors = false
    // b.actorSystem = b.actorSystem
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
}

/** The `FScape` trait is the basic entity representing a sound process. */
trait FScape[S <: Sys[S]] extends Obj[S] with Publisher[S, FScape.Update[S]] {
  /** The variable synth graph function of the process. */
  def graph: GraphObj.Var[S]

  def outputs: FScape.Outputs[S]

  def run(config: Control.Config = FScape.defaultConfig, attr: Runner.Attr[S] = Runner.emptyAttr[S])
         (implicit tx: S#Tx, universe: Universe[S]): FScape.Rendering[S]
}