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

package de.sciss.proc

import java.net.URI
import java.util

import de.sciss.fscape.graph.{Constant, ConstantD, ConstantI, ConstantL}
import de.sciss.fscape.lucre.impl.UGenGraphBuilderContextImpl
import de.sciss.fscape.stream.{Control => SControl}
import de.sciss.fscape.{Graph, Lazy}
import de.sciss.lucre.Event.Targets
import de.sciss.lucre.impl.{DummyEvent, ExprTypeImpl}
import de.sciss.lucre.{Artifact, Copy, Disposable, Elem, Event, EventLike, Expr, Ident, Obj, Observable, Publisher, Txn, Var => LVar, Workspace => LWorkspace}
import de.sciss.model.{Change => MChange}
import de.sciss.proc.Code.{Example, Import}
import de.sciss.proc.impl.{CodeImpl, FScapeImpl, FScapeOutputGenViewImpl, FScapeOutputImpl, FScapeRunnerImpl, FScapeRenderingImpl}
import de.sciss.serial.{ConstFormat, DataInput, DataOutput, TFormat}
import de.sciss.{fscape, model, proc}

import scala.annotation.{switch, tailrec}
import scala.collection.immutable.{IndexedSeq => Vec, Seq => ISeq}
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

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
    // b.actorTxntem = b.actorTxntem
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

      // we use an identity hash map, because we do _not_
      // want to alias objects in the serialization; the input
      // is an in-memory object graph.
      private type RefMapOut = util.IdentityHashMap[Product, Integer]

      private final class RefMapIn {
        var map   = Map.empty[Int, Product]
        var count = 0
      }

      private def writeProduct(p: Product, out: DataOutput, ref: RefMapOut): Unit = {
        val id0Ref = ref.get(p)
        if (id0Ref != null) {
          out.writeByte('<')
          out.writeInt(id0Ref)
          return
        }
        out.writeByte('P')
        val pck     = p.getClass.getPackage.getName
        val prefix  = p.productPrefix
        val name    = if (pck == "de.sciss.fscape.graph") prefix else s"$pck.$prefix"
        out.writeUTF(name)
        out.writeShort(p.productArity)
        p.productIterator.foreach(writeElem(_, out, ref))

        val id     = ref.size() // count
        ref.put(p, id)
        ()
      }

      private def writeElemSeq(xs: Seq[Any], out: DataOutput, ref: RefMapOut): Unit = {
        out.writeByte('X')
        out.writeInt(xs.size)
        xs.foreach(writeElem(_, out, ref))
      }

      @tailrec
      private def writeElem(e: Any, out: DataOutput, ref: RefMapOut): Unit =
        e match {
          case c: Constant =>
            out.writeByte('C')
            if (c.isDouble) {
              out.writeByte('d')
              out.writeDouble(c.doubleValue)
            } else if (c.isInt) {
              out.writeByte('i')
              out.writeInt(c.intValue)
            } else if (c.isLong) {
              out.writeByte('l')
              out.writeLong(c.longValue)
            }
          //        case r: MaybeRate =>
          //          out.writeByte('R')
          //          out.writeByte(r.id)
          case o: Option[_] =>
            out.writeByte('O')
            out.writeBoolean(o.isDefined)
            if (o.isDefined) writeElem(o.get, out, ref)
          case xs: Seq[_] =>  // 'X'. either indexed seq or var arg (e.g. wrapped array)
            writeElemSeq(xs, out, ref)
          case y: Graph =>  // important to handle Graph explicitly, as `apply` is overloaded!
            out.writeByte('Y')
            writeIdentifiedGraph(y, out, ref)
          // important: `Product` must come after all other types that might _also_ be a `Product`
          case p: Product =>
            writeProduct(p, out, ref) // 'P' or '<'
          case i: Int =>
            out.writeByte('I')
            out.writeInt(i)
          case s: String =>
            out.writeByte('S')
            out.writeUTF(s)
          case b: Boolean   =>
            out.writeByte('B')
            out.writeBoolean(b)
          case f: Float =>
            out.writeByte('F')
            out.writeFloat(f)
          case d: Double =>
            out.writeByte('D')
            out.writeDouble(d)
          case n: Long =>
            out.writeByte('L')
            out.writeLong(n)
          case _: Unit =>
            out.writeByte('U')
          //        case f: File =>
          case u: URI =>
            out.writeByte('u')
            Artifact.Value.write(u, out)
        }

      def write(v: Graph, out: DataOutput): Unit = {
        out.writeShort(SER_VERSION)
        val ref = new RefMapOut
        writeIdentifiedGraph(v, out, ref)
      }

      private def writeIdentifiedGraph(v: Graph, out: DataOutput, ref: RefMapOut): Unit = {
        writeElemSeq(v.sources, out, ref)
        //      val ctl = v.controlProxies
        //      out.writeByte('T')
        //      out.writeInt(ctl.size)
        //      ctl.foreach(writeProduct(_, out, ref))
      }

      // expects that 'X' byte has already been read
      private def readIdentifiedSeq(in: DataInput, ref: RefMapIn): Seq[Any] = {
        val num = in.readInt()
        Vector.fill(num)(readElem(in, ref))
      }

      // expects that 'P' byte has already been read
      private def readIdentifiedProduct(in: DataInput, ref: RefMapIn): Product = {
        val prefix    = in.readUTF()
        val arity     = in.readShort()
        val className = if (Character.isUpperCase(prefix.charAt(0))) s"de.sciss.fscape.graph.$prefix" else prefix

        val res = try {
          if (arity == 0 && className.charAt(className.length - 1) == '$') {
            // case object
            val companion = Class.forName(s"$className").getField("MODULE$").get(null)
            companion.asInstanceOf[Product]

          } else {

            // cf. stackoverflow #3039822
            val companion = Class.forName(s"$className$$").getField("MODULE$").get(null)
            val elems = new Array[AnyRef](arity)
            var i = 0
            while (i < arity) {
              elems(i) = readElem(in, ref).asInstanceOf[AnyRef]
              i += 1
            }
            //    val m         = companion.getClass.getMethods.find(_.getName == "apply")
            //      .getOrElse(sys.error(s"No apply method found on $companion"))
            val ms = companion.getClass.getMethods
            var m = null: java.lang.reflect.Method
            var j = 0
            while (m == null && j < ms.length) {
              val mj = ms(j)
              if (mj.getName == "apply" && mj.getParameterTypes.length == arity) m = mj
              j += 1
            }
            if (m == null) sys.error(s"No apply method found on $companion")

            m.invoke(companion, elems: _*).asInstanceOf[Product]
          }

        } catch {
          case NonFatal(e) =>
            throw new IllegalArgumentException(s"While de-serializing $prefix", e)
        }

        val id        = ref.count
        ref.map      += ((id, res))
        ref.count     = id + 1
        res
      }

      private def readElem(in: DataInput, ref: RefMapIn): Any = {
        (in.readByte(): @switch) match {
          case 'C' =>
            (in.readByte(): @switch) match {
              case 'd' => ConstantD(in.readDouble())
              case 'i' => ConstantI(in.readInt   ())
              case 'l' => ConstantL(in.readLong  ())
            }
          //        case 'R' => MaybeRate(in.readByte())
          case 'O' => if (in.readBoolean()) Some(readElem(in, ref)) else None
          case 'X' => readIdentifiedSeq(in, ref)
          case 'Y' => readIdentifiedGraph  (in, ref)
          case 'P' => readIdentifiedProduct(in, ref)
          case '<' =>
            val id = in.readInt()
            ref.map(id)
          case 'I' => in.readInt()
          case 'S' => in.readUTF()
          case 'B' => in.readBoolean()
          case 'F' => in.readFloat()
          case 'D' => in.readDouble()
          case 'L' => in.readLong()
          case 'U' => ()
          case 'u' =>
            Artifact.Value.read(in)
          case 'f' =>   // backwards compatible
            val path = in.readUTF()
            Artifact.fileToURI(path)
        }
      }

      def read(in: DataInput): Graph = {
        val cookie  = in.readShort()
        require(cookie == SER_VERSION, s"Unexpected cookie $cookie")
        val ref = new RefMapIn
        readIdentifiedGraph(in, ref)
      }

      private def readIdentifiedGraph(in: DataInput, ref: RefMapIn): Graph = {
        val b1 = in.readByte()
        require(b1 == 'X')    // expecting sequence
        val numSources  = in.readInt()
        val sources     = Vector.fill(numSources) {
          readElem(in, ref).asInstanceOf[Lazy]
        }
        //      val b2 = in.readByte()
        //      require(b2 == 'T')    // expecting set
        //      val numControls = in.readInt()
        //      val controls    = Set.newBuilder[ControlProxyLike] // stupid Set doesn't have `fill` and `tabulate` methods
        //      var i = 0
        //      while (i < numControls) {
        //        controls += readElem(in, ref).asInstanceOf[ControlProxyLike]
        //        i += 1
        //      }
        Graph(sources /* , controls.result() */)
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