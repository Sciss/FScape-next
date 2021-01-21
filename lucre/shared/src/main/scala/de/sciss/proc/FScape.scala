/*
 *  FScape.scala
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

package de.sciss.proc

import de.sciss.fscape.Graph
import de.sciss.fscape.Graph.ProductReader
import de.sciss.fscape.lucre.impl.UGenGraphBuilderContextImpl
import de.sciss.fscape.stream.{Control => SControl}
import de.sciss.lucre.Event.Targets
import de.sciss.lucre.impl.{DummyEvent, ExprTypeImpl}
import de.sciss.lucre.{Copy, Disposable, Elem, Event, EventLike, Expr, Ident, Obj, Observable, Publisher, Txn, Var => LVar, Workspace => LWorkspace}
import de.sciss.model.{Change => MChange}
import de.sciss.proc.Code.{Example, Import}
import de.sciss.proc.impl.{CodeImpl, FScapeImpl, FScapeOutputGenViewImpl, FScapeOutputImpl, FScapeRenderingImpl, FScapeRunnerImpl}
import de.sciss.serial.{ConstFormat, DataInput, DataOutput, TFormat}
import de.sciss.{fscape, model, proc}

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

    Graph.addProductReaderSq({
      import de.sciss.fscape.graph._
      import de.sciss.fscape.lucre.{graph => l}
      Seq[ProductReader[Product]](
        AffineTransform2D,
        ARCWindow,
        ArithmSeq,
        BinaryOp,
        Biquad,
        Bleach,
        Blobs2D,
        BufferMemory,
        ChannelProxy,
        Clip,
        CombN,
        ComplexBinaryOp,
        ComplexUnaryOp,
        Concat,
        ConstQ,
        ControlBlockSize,
        Convolution,
        DC,
        DCT_II,
        DebugSink,
        DebugThrough,
        DelayN,
        DEnvGen,
        DetectLocalMax,
        Differentiate,
        Distinct,
        Done,
        Drop,
        DropRight,
        DropWhile,
        Elastic,
        Empty,
        ExpExp,
        ExpLin,
        /* FFT: */ Real1FFT, Real1IFFT, Real1FullFFT, Real1FullIFFT, Complex1FFT, Complex1IFFT,
        /* FFT2: */ Real2FFT, Real2IFFT, Real2FullFFT, Real2FullIFFT, Complex2FFT, Complex2IFFT,
        FilterSeq,
        Flatten,
        Fold,
        FoldCepstrum,
        Frames, Indices,
        Gate,
        GenWindow,
        GeomSeq,
        impl.GESeq,
        GimpSlur,
        GramSchmidtMatrix,
        Hash,
        HilbertCurve.From2D, HilbertCurve.To2D,
        Histogram,
        HPF,
        /* IfElse: */ IfThen, ElseIfThen, ElseUnit, ElseGE,
        ImageFile.Type.PNG, ImageFile.Type.JPG, ImageFile.SampleFormat.Int8, ImageFile.SampleFormat.Int16,
        ImageFile.Spec,
        Impulse,
        Latch,
        LeakDC,
        Length,
        LFSaw,
        Limiter,
        Line,
        LinExp,
        LinKernighanTSP,
        LinLin,
        Loudness,
        LPF,
        Masking2D,
        MatchLen,
        MatrixInMatrix,
        MatrixOutMatrix,
        MelFilter,
        Metro,
        Mix.MonoEqP,
        NormalizeWindow,
        NumChannels,
        OffsetOverlapAdd,
        OnePole,
        OnePoleWindow,
        OverlapAdd,
        PeakCentroid1D, PeakCentroid2D,
        Pearson,
        PenImage,
        PitchesToViterbi,
        Poll,
        PriorityQueue,
        Progress,
        ProgressFrames,
        Reduce,
        ReduceWindow,
        RepeatWindow,
        Resample,
        ResizeWindow,
        ReverseWindow,
        RotateFlipMatrix,
        RotateWindow,
        RunningMax,
        RunningMin,
        RunningProduct,
        RunningSum,
        RunningWindowMax,
        RunningWindowMin,
        RunningWindowProduct,
        RunningWindowSum,
        ScanImage,
        SegModPhasor,
        SetResetFF,
        SinOsc,
        Sliding,
        SlidingPercentile,
        SlidingWindowPercentile,
        SortWindow,
        StrongestLocalMaxima,
        ScanImage,
        SegModPhasor,
        SetResetFF,
        SinOsc,
        Sliding,
        SlidingPercentile,
        SlidingWindowPercentile,
        SortWindow,
        StrongestLocalMaxima,
        Take,
        TakeRight,
        TakeWhile,
        Timer,
        TransposeMatrix,
        Trig,
        TrigHold,
        UnaryOp,
        UnzipWindow, UnzipWindowN,
        ValueIntSeq, ValueLongSeq, ValueDoubleSeq,
        Viterbi,
        WhiteNoise,
        WindowApply,
        WindowIndexWhere,
        WindowMaxIndex,
        Wrap,
        Zip,
        ZipWindow, ZipWindowN,
        // lucre
        l.Action,
        l.Attribute, l.Attribute.Scalar, l.Attribute.Vector,
        l.AudioFileIn, l.AudioFileIn.NumFrames, l.AudioFileIn.SampleRate, l.AudioFileIn.WithCue,
        l.AudioFileOut, l.AudioFileOut.WithFile,
        l.MkDouble,
        l.MkDoubleVector,
        l.MkInt,
        l.MkIntVector,
        l.MkLong,
        l.OnComplete,
      )
    })

    FScapeImpl.init()
  }

  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T] = FScapeImpl()

  def read[T <: Txn[T]](in: DataInput)(implicit tx: T): FScape[T] = FScapeImpl.read(in)

  implicit def format[T <: Txn[T]]: TFormat[T, FScape[T]] = FScapeImpl.format[T]

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
    FScapeImpl.readIdentifiedObj(in)

  // ----

  object Rendering {
    type State                                    = GenView.State
    val  Completed: GenView.Completed       .type = GenView.Completed
    val  Running  : GenView.Running         .type = GenView.Running
    type Running                                  = GenView.Running

    val  Cancelled: fscape.stream.Cancelled .type = fscape.stream.Cancelled
    type Cancelled                                = fscape.stream.Cancelled

    /** Creates a view with the default `UGenGraphBuilder.Context`. */
    def apply[T <: Txn[T]](peer: FScape[T], config: SControl.Config, attr: Runner.Attr[T] = Runner.emptyAttr[T])
                          (implicit tx: T, universe: Universe[T]): Rendering[T] = {
      val ugbCtx = new UGenGraphBuilderContextImpl.Default(peer, attr = attr)
      FScapeRenderingImpl(peer, ugbCtx, config, force = true)
    }
  }
  trait Rendering[T <: Txn[T]] extends Observable[T, Rendering.State] with Disposable[T] {
    def state(implicit tx: T): Rendering.State

    def result(implicit tx: T): Option[Try[Unit]]

    def outputResult(output: Output.GenView[T])(implicit tx: T): Option[Try[Obj[T]]]

    def control: SControl

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
      CodeImpl.compileBody[In, Out, Unit, Code](this, classOf[Unit])
    }

    def execute(in: In)(implicit compiler: proc.Code.Compiler): Out =
      Graph {
        CodeImpl.compileThunk[Unit](this, classOf[Unit], execute = true)
      }

    def prelude : String = "object Main {\n"

    def postlude: String = "\n}\n"

    def updateSource(newText: String): Code = copy(source = newText)
  }

  object Output extends Obj.Type {
    final val typeId = 0x1000D

    def read[T <: Txn[T]](in: DataInput)(implicit tx: T): Output[T] = FScapeOutputImpl.read(in)

    implicit def format[T <: Txn[T]]: TFormat[T, Output[T]] = FScapeOutputImpl.format

    override def readIdentifiedObj[T <: Txn[T]](in: DataInput)(implicit tx: T): Obj[T] =
      FScapeOutputImpl.readIdentifiedObj(in)

    trait Reader {
      def key: String
      def tpe: Obj.Type

      def readOutputValue(in: DataInput): Any

      def readOutput[T <: Txn[T]](in: DataInput)(implicit tx: T, workspace: LWorkspace[T]): Obj[T]
    }

    trait Writer extends de.sciss.serial.Writable {
      def outputValue: Any
    }

    object GenView {
      def apply[T <: Txn[T]](config: SControl.Config, output: Output[T], rendering: Rendering[T])
                            (implicit tx: T, context: GenContext[T]): GenView[T] =
        FScapeOutputGenViewImpl(config, output, rendering)
    }
    trait GenView[T <: Txn[T]] extends proc.GenView[T] {
      def output(implicit tx: T): Output[T]
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

  def genViewFactory(config: SControl.Config = defaultConfig): GenView.Factory =
    FScapeImpl.genViewFactory(config)

  @volatile
  private[this] var _defaultConfig: SControl.Config = _

  private lazy val _lazyDefaultConfig: SControl.Config = {
    val b             = SControl.Config()
    b.useAsync        = false
    b.terminateActors = false
    b
  }

  /** There is currently a problem with building `Config().build` multiple times,
    * in that we create new actor systems and materializers that will not be shut down,
    * unless an actual rendering is performed. As a work around, use this single
    * instance which will reuse one and the same actor system.
    */
  def defaultConfig: SControl.Config = {
    if (_defaultConfig == null) _defaultConfig = _lazyDefaultConfig
    _defaultConfig
  }

  def defaultConfig_=(value: SControl.Config): Unit =
    _defaultConfig = value

  type Config = SControl.Config
  val  Config = SControl.Config

  // ---- GraphObj ----

  object GraphObj extends ExprTypeImpl[Graph, GraphObj] {
    final val typeId = 100

    def tryParse(value: Any): Option[Graph] = value match {
      case x: Graph => Some(x)
      case _        => None
    }

    protected def mkConst[T <: Txn[T]](id: Ident[T], value: A)(implicit tx: T): Const[T] =
      new _Const[T](id, value)

    protected def mkVar[T <: Txn[T]](targets: Targets[T], vr: LVar[T, E[T]], connect: Boolean)
                                    (implicit tx: T): Var[T] = {
      val res = new _Var[T](targets, vr)
      if (connect) res.connect()
      res
    }

    private final class _Const[T <: Txn[T]](val id: Ident[T], val constValue: A)
      extends ConstImpl[T] with GraphObj[T]

    private final class _Var[T <: Txn[T]](val targets: Targets[T], val ref: LVar[T, E[T]])
      extends VarImpl[T] with GraphObj[T]

    /** A format for graphs. */
    object valueFormat extends ConstFormat[Graph] {
      private final val SER_VERSION = 0x5347

      def write(v: Graph, out: DataOutput): Unit = {
        out.writeShort(SER_VERSION)
        val ref = new Graph.RefMapOut(out)
        ref.writeIdentifiedGraph(v)
      }

      def read(in: DataInput): Graph = {
        val cookie  = in.readShort()
        require(cookie == SER_VERSION, s"Unexpected cookie $cookie")
        val ref = new Graph.RefMapIn(in)
        ref.readIdentifiedGraph()
      }
    }

    private final val emptyCookie = 4

    override protected def readCookie[T <: Txn[T]](in: DataInput, cookie: Byte)
                                                  (implicit tx: T): E[T] =
      cookie match {
        case `emptyCookie` =>
          val id = tx.readId(in)
          new Predefined(id, cookie)
        case _ => super.readCookie(in, cookie)
      }

    private val emptyGraph = Graph {}

    def empty[T <: Txn[T]](implicit tx: T): E[T] = apply(emptyCookie  )

    private def apply[T <: Txn[T]](cookie: Int)(implicit tx: T): E[T] = {
      val id = tx.newId()
      new Predefined(id, cookie)
    }

    private final class Predefined[T <: Txn[T]](val id: Ident[T], cookie: Int)
      extends GraphObj[T] with Expr.Const[T, Graph] {

      def event(slot: Int): Event[T, Any] = throw new UnsupportedOperationException

      def tpe: Obj.Type = GraphObj

      def copy[Out <: Txn[Out]]()(implicit tx: T, txOut: Out, context: Copy[T, Out]): Elem[Out] =
        new Predefined(txOut.newId(), cookie) // .connect()

      def write(out: DataOutput): Unit = {
        out.writeInt(tpe.typeId)
        out.writeByte(cookie)
        id.write(out)
      }

      def value(implicit tx: T): Graph = constValue

      def changed: EventLike[T, MChange[Graph]] = DummyEvent()

      def dispose()(implicit tx: T): Unit = ()

      def constValue: Graph = cookie match {
        case `emptyCookie` => emptyGraph
      }
    }
  }
  trait GraphObj[T <: Txn[T]] extends Expr[T, Graph]
}

/** The `FScape` trait is the basic entity representing a sound process. */
trait FScape[T <: Txn[T]] extends Obj[T] with Publisher[T, FScape.Update[T]] {
  /** The variable synth graph function of the process. */
  def graph: FScape.GraphObj.Var[T]

  def outputs: FScape.Outputs[T]

  def run(config: SControl.Config = FScape.defaultConfig, attr: Runner.Attr[T] = Runner.emptyAttr[T])
         (implicit tx: T, universe: Universe[T]): FScape.Rendering[T]
}