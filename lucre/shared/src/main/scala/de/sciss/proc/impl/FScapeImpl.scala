/*
 *  FScapeImpl.scala
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

package de.sciss.proc.impl

import de.sciss.fscape.stream.Control
import de.sciss.lucre.Event.Targets
import de.sciss.lucre.data.SkipList
import de.sciss.lucre.impl.{GeneratorEvent, ObjFormat, SingleEventNode}
import de.sciss.lucre.{AnyTxn, Copy, Elem, Obj, Pull, Txn, synth}
import de.sciss.serial.{DataInput, DataOutput, TFormat}
import de.sciss.proc.FScape.{Output, Rendering}
import de.sciss.proc.{FScape, GenView, Runner, Universe}

import scala.collection.immutable.{IndexedSeq => Vec}

object FScapeImpl {
  private final val SER_VERSION_OLD = 0x4673  // "Fs"
  private final val SER_VERSION     = 0x4674

  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T] = new New[T]

  def read[T <: Txn[T]](in: DataInput)(implicit tx: T): FScape[T] =
    format[T].readT(in)

  def format[T <: Txn[T]]: TFormat[T, FScape[T]] = anyFmt.asInstanceOf[Fmt[T]]

  private val anyFmt = new Fmt[AnyTxn]

  private class Fmt[T <: Txn[T]] extends ObjFormat[T, FScape[T]] {
    def tpe: Obj.Type = FScape
  }

  def readIdentifiedObj[T <: Txn[T]](in: DataInput)(implicit tx: T): FScape[T] = {
    val targets = Targets.read(in)
    val serVer  = in.readShort()
    if (serVer == SER_VERSION) {
      new Read(in, targets)
    } else if (serVer == SER_VERSION_OLD) {
      new ReadOLD(in, targets)
    } else {
      sys.error(s"Incompatible serialized (found $serVer, required $SER_VERSION)")
    }
  }

//  // ---- Code ----
//
//  implicit object CodeWrapper extends CodeImpl.Wrapper[Unit, Graph, Unit, FScape.Code] {
//    def id      : Int             = FScape.Code.id
//    def binding : Option[String]  = None
//
//    def wrap(in: Unit)(fun: => Unit): Graph = Graph(fun)
//
//    def blockTag = "Unit"
//  }

  // ---- GenView ----

  def genViewFactory(config: Control.Config): GenView.Factory = new OutputGenViewFactory(config)

  private final class OutputGenViewFactory(config: Control.Config) extends GenView.Factory {
    def typeId: Int = Output.typeId

    type Repr[~ <: Txn[~]] = Output[~]

    def apply[T <: synth.Txn[T]](output: Output[T])(implicit tx: T, universe: Universe[T]): GenView[T] = {
      val _fscape = output.fscape
      import universe.genContext
      val fscView = genContext.acquire[Rendering[T]](_fscape) {
        FScapeRenderingImpl(_fscape, config, attr = Runner.emptyAttr[T], force = false)
      }
      Output.GenView(config, output, fscView)
    }
  }

  private sealed trait Base[T <: Txn[T]] {
    _: FScape[T] =>

    final def tpe: Obj.Type = FScape

    override def toString: String = s"FScape$id"

    // --- rendering ---

    final def run(config: Control.Config, attr: Runner.Attr[T])(implicit tx: T, universe: Universe[T]): Rendering[T] =
      Rendering(this, config, attr = attr)
  }

  private sealed trait ImplOLD[T <: Txn[T]]
    extends FScape[T] with Base[T] with SingleEventNode[T, FScape.Update[T]] {
    proc =>

    def copy[Out <: Txn[Out]]()(implicit tx: T, txOut: Out, context: Copy[T, Out]): Elem[Out] =
      new Impl[Out] { out =>
        protected val targets: Targets[Out] = Targets[Out]()
        val graph     : FScape.GraphObj.Var[Out]                = context(proc.graph)
        val outputsMap: SkipList.Map[Out, String, Output[Out]]  = SkipList.Map.empty
        connect()
      }

    import FScape._

    // ---- key maps ----

    def isConnected(implicit tx: T): Boolean = targets.nonEmpty

    final def connect()(implicit tx: T): this.type = {
      graph.changed ---> changed
      this
    }

    private def disconnect()(implicit tx: T): Unit = {
      graph.changed -/-> changed
    }

    object changed extends Changed
      with GeneratorEvent[T, FScape.Update[T]] {
      def pullUpdate(pull: Pull[T])(implicit tx: T): Option[FScape.Update[T]] = {
        val graphCh     = graph.changed
        val graphOpt    = if (pull.contains(graphCh)) pull(graphCh) else None
        val stateOpt    = Option.empty[FScape.Update[T]] // if (pull.isOrigin(this)) Some(pull.resolve[FScape.Update[T]]) else None

        val seq0 = graphOpt.fold(Vec.empty[Change[T]]) { u =>
          Vector(GraphChange(u))
        }

        val seq3 = stateOpt.fold(seq0) { u =>
          if (seq0.isEmpty) u.changes else seq0 ++ u.changes
        }
        if (seq3.isEmpty) None else Some(FScape.Update(proc, seq3))
      }
    }

    final protected def writeData(out: DataOutput): Unit = {
      out.writeShort(SER_VERSION_OLD)
      graph.write(out)
    }

    final protected def disposeData()(implicit tx: T): Unit = {
      disconnect()
      graph.dispose()
    }

    // dummy
    object outputs extends Outputs[T] {
      def get(key: String)(implicit tx: T): Option[Output[T]] = None

      def keys(implicit tx: T): Set[String] = Set.empty

      def iterator(implicit tx: T): Iterator[Output[T]] = Iterator.empty

      def add(key: String, tpe: Obj.Type)(implicit tx: T): Output[T] =
        throw new UnsupportedOperationException(s"Old FScape object without actual outputs, cannot call `add`")

      def remove(key: String)(implicit tx: T): Boolean = false
    }
  }

  private sealed trait Impl[T <: Txn[T]]
    extends FScape[T] with Base[T] with SingleEventNode[T, FScape.Update[T]] {
    proc =>

    // --- abstract ----

    protected def outputsMap: SkipList.Map[T, String, Output[T]]

    // --- impl ----

    def copy[Out <: Txn[Out]]()(implicit tx: T, txOut: Out, context: Copy[T, Out]): Elem[Out] =
      new Impl[Out] { out =>
        protected val targets: Targets[Out]                     = Targets[Out]()
        val graph     : FScape.GraphObj.Var[Out]                = context(proc.graph)
        val outputsMap: SkipList.Map[Out, String, Output[Out]]  = SkipList.Map.empty

        context.defer(proc, out) {
          def copyMap(in : SkipList.Map[T  , String, Output[T  ]],
                      out: SkipList.Map[Out, String, Output[Out]]): Unit =
            in.iterator.foreach { case (key, eIn) =>
              val eOut = context(eIn)
              out.put(key, eOut)
            }

          // copyMap(proc.scanInMap , out.scanInMap)
          copyMap(proc.outputsMap, out.outputsMap)
        }
        connect()
      }

    import FScape._

    // ---- key maps ----

    def isConnected(implicit tx: T): Boolean = targets.nonEmpty

    final def connect()(implicit tx: T): this.type = {
      graph.changed ---> changed
      this
    }

    private def disconnect()(implicit tx: T): Unit = {
      graph.changed -/-> changed
    }

    object changed extends Changed
      with GeneratorEvent[T, FScape.Update[T]] {
      def pullUpdate(pull: Pull[T])(implicit tx: T): Option[FScape.Update[T]] = {
        val graphCh     = graph.changed
        val graphOpt    = if (pull.contains(graphCh)) pull(graphCh) else None
        val stateOpt    = if (pull.isOrigin(this)) Some(pull.resolve[FScape.Update[T]]) else None

        val seq0 = graphOpt.fold(Vec.empty[Change[T]]) { u =>
          Vector(GraphChange(u))
        }

        val seq3 = stateOpt.fold(seq0) { u =>
          if (seq0.isEmpty) u.changes else seq0 ++ u.changes
        }
        if (seq3.isEmpty) None else Some(FScape.Update(proc, seq3))
      }
    }

    final protected def writeData(out: DataOutput): Unit = {
      out.writeShort(SER_VERSION)
      graph     .write(out)
      outputsMap.write(out)
    }

    final protected def disposeData()(implicit tx: T): Unit = {
      disconnect()
      graph     .dispose()
      outputsMap.dispose()
    }

    object outputs extends Outputs[T] {
      protected def fire(added: Option[Output[T]], removed: Option[Output[T]])
                        (implicit tx: T): Unit = {
        val b = Vector.newBuilder[FScape.OutputsChange[T]]
        b.sizeHint(2)
        // convention: first the removals, then the additions. thus, overwriting a key yields
        // successive removal and addition of the same key.
        removed.foreach { output =>
          b += FScape.OutputRemoved[T](output)
        }
        added.foreach { output =>
          b += FScape.OutputAdded  [T](output)
        }

        proc.changed.fire(FScape.Update(proc, b.result()))
      }

      private def add(key: String, value: Output[T])(implicit tx: T): Unit = {
        val optRemoved = outputsMap.put(key, value)
        fire(added = Some(value), removed = optRemoved)
      }

      def remove(key: String)(implicit tx: T): Boolean =
        outputsMap.remove(key).exists { output =>
          fire(added = None, removed = Some(output))
          true
        }

      def add(key: String, tpe: Obj.Type)(implicit tx: T): Output[T] =
        get(key).getOrElse {
          val res = FScapeOutputImpl[T](proc, key, tpe)
          add(key, res)
          res
        }

      def get(key: String)(implicit tx: T): Option[Output[T]] = outputsMap.get(key)

      def keys(implicit tx: T): Set[String] = outputsMap.keysIterator.toSet

      def iterator(implicit tx: T): Iterator[Output[T]] = outputsMap.iterator.map(_._2)
    }
  }

  private final class New[T <: Txn[T]](implicit tx0: T) extends Impl[T] {
    protected val targets: Targets[T] = Targets()(tx0)
    val graph     : FScape.GraphObj.Var[T]              = FScape.GraphObj.newVar(FScape.GraphObj.empty)
    val outputsMap: SkipList.Map[T, String, Output[T]]  = SkipList.Map.empty
    connect()(tx0)
  }

  private final class ReadOLD[T <: Txn[T]](in: DataInput, protected val targets: Targets[T])
                                          (implicit tx0: T)
    extends ImplOLD[T] {

    val graph: FScape.GraphObj.Var[T] = FScape.GraphObj.readVar(in)
  }

  private final class Read[T <: Txn[T]](in: DataInput, protected val targets: Targets[T])
                                       (implicit tx0: T)
    extends Impl[T] {

    val graph     : FScape.GraphObj.Var[T]              = FScape.GraphObj .readVar(in)
    val outputsMap: SkipList.Map[T, String, Output[T]]  = SkipList.Map    .read   (in)
  }
}