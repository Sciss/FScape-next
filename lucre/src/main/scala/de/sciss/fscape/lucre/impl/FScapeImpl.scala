/*
 *  FScapeImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre
package impl

import de.sciss.fscape.lucre.FScape.Rendering.State
import de.sciss.fscape.lucre.FScape.{Output, Rendering}
import de.sciss.fscape.stream.Control
import de.sciss.lucre.data.SkipList
import de.sciss.lucre.event.Targets
import de.sciss.lucre.event.impl.ObservableImpl
import de.sciss.lucre.stm.impl.ObjSerializer
import de.sciss.lucre.stm.{Copy, Disposable, Elem, NoSys, Obj, Sys, TxnLike}
import de.sciss.lucre.{stm, event => evt}
import de.sciss.serial.{DataInput, DataOutput, Serializer}
import de.sciss.synth.proc.impl.CodeImpl
import de.sciss.synth.proc.{GenContext, GenView, WorkspaceHandle}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.stm.Ref
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

object FScapeImpl {
  private final val SER_VERSION_OLD = 0x4673  // "Fs"
  private final val SER_VERSION     = 0x4674

  def apply[S <: Sys[S]](implicit tx: S#Tx): FScape[S] = new New[S]

  def read[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): FScape[S] =
    serializer[S].read(in, access)

  def serializer[S <: Sys[S]]: Serializer[S#Tx, S#Acc, FScape[S]] = anySer.asInstanceOf[Ser[S]]

  private val anySer = new Ser[NoSys]

  private class Ser[S <: Sys[S]] extends ObjSerializer[S, FScape[S]] {
    def tpe: Obj.Type = FScape
  }

  def readIdentifiedObj[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): FScape[S] = {
    val targets = Targets.read(in, access)
    val serVer  = in.readShort()
    if (serVer == SER_VERSION) {
      new Read(in, access, targets)
    } else if (serVer == SER_VERSION_OLD) {
      new ReadOLD(in, access, targets)
    } else {
      sys.error(s"Incompatible serialized (found $serVer, required $SER_VERSION)")
    }
  }

  // ---- Code ----

  implicit object CodeWrapper extends CodeImpl.Wrapper[Unit, Graph, FScape.Code] {
    def id: Int = FScape.Code.id
    def binding = None

    def wrap(in: Unit)(fun: => Any): Graph = Graph(fun)

    def blockTag = "Unit"
  }

  // ---- GenView ----

  def genViewFactory(config: Control.Config): GenView.Factory = new OutputGenViewFactory(config)

  private final class OutputGenViewFactory(config: Control.Config) extends GenView.Factory {
    def typeID: Int = Output.typeID

    type Repr[~ <: Sys[~]] = Output[~]

    def apply[S <: Sys[S]](output: Output[S])(implicit tx: S#Tx, context: GenContext[S]): GenView[S] =
      new OutputGenView(config, tx.newHandle(output), output.valueType)
  }

  private final class OutputGenView[S <: Sys[S]](config: Control.Config,
                                                 override val obj: stm.Source[S#Tx, Output[S]],
                                                 val valueType: Obj.Type)
                                                (implicit context: GenContext[S])
    extends GenView[S] with ObservableImpl[S, GenView.State] { view =>

    private[this] val _state      = Ref[GenView.State](GenView.Stopped)
    private[this] val _rendering  = Ref(Option.empty[(Rendering[S], Disposable[S#Tx])])

    def typeID: Int = Output.typeID

    def value(implicit tx: S#Tx): Option[Try[Obj[S]]] = obj().value

//    def state(implicit tx: S#Tx): GenView.State =
//      _rendering.get(tx.peer).fold[GenView.State](GenView.Stopped)(_.state)

    def state(implicit tx: S#Tx): GenView.State = _state.get(tx.peer)

    def fscape(implicit tx: S#Tx): FScape[S] = obj().fscape

    private def state_=(st: GenView.State)(implicit tx: S#Tx): Unit = {
      val stOld = _state.swap(st)(tx.peer)
      if (st.isComplete) {
        disposeObserver()
      }
      if (st != stOld) view.fire(st)
    }

    private def mkObserver(r: Rendering[S])(implicit tx: S#Tx): Unit = {
      val obs = r.react { implicit tx => st =>
        state_=(st)
      }
      disposeObserver()
      _rendering.set(Some((r, obs)))(tx.peer)
      state_=(r.state)
    }

    private def disposeObserver()(implicit tx: S#Tx): Unit =
      _rendering.swap(None)(tx.peer).foreach { case (r, obs) =>
        obs.dispose()
        val _fscape = fscape
        context.release(_fscape)
      }

    def start()(implicit tx: S#Tx): Unit = {
      val isRunning = _rendering.get(tx.peer).exists { case (r, _) =>
        r.state.isComplete
      }
      if (!isRunning) startNew()
    }

    private def startNew()(implicit tx: S#Tx): Unit = {
      val _fscape = fscape
      // the idea is that each of the output views can
      // trigger the rendering, but we ensure there is
      // always no more than one rendering (per context)
      // running.
      val r = context.acquire[Rendering[S]](_fscape) {
        import context.{cursor, workspaceHandle}
        val g   = _fscape.graph().value
        val _r  = new RenderingImpl[S](config)
        _r.start(_fscape, g)
        _r
      }
      mkObserver(r)
    }

    def stop()(implicit tx: S#Tx): Unit =
      _rendering.get(tx.peer).foreach { case (r, _) =>
        r.cancel()
      }

    def dispose()(implicit tx: S#Tx): Unit = disposeObserver()
  }

  // ---- Rendering ----

  private final class RenderingImpl[S <: Sys[S]](config: Control.Config)(implicit cursor: stm.Cursor[S])
    extends Rendering[S] with ObservableImpl[S, Rendering.State] {

    private[this] val _state        = Ref[Rendering.State](Rendering.Stopped)
    private[this] val _result       = Ref(Option.empty[Try[Unit]])
    private[this] val _disposed     = Ref(false)
    implicit val control: Control   = Control(config)

    def result(implicit tx: S#Tx): Option[Try[Unit]] = _result.get(tx.peer)

    def reactNow(fun: (S#Tx) => (State) => Unit)(implicit tx: S#Tx): Disposable[S#Tx] = {
      val res = react(fun)
      fun(tx)(state)
      res
    }

    private def completeWith(t: Try[Unit]): Unit = if (!_disposed.single.get)
      cursor.step { implicit tx =>
        import TxnLike.peer
        if (!_disposed()) {
          state_=(Rendering.Completed, Some(t))
          //        t match {
          //          case Success(())          => state = Rendering.Success
          //          case Failure(ex)          => state = Rendering.Failure(ex)
          //        }
        }
      }

    def start(f: FScape[S], graph: Graph)(implicit tx: S#Tx, workspace: WorkspaceHandle[S]): Unit = {
      try {
        val ugens = UGenGraphBuilder.build(f, graph)
        tx.afterCommit {
          try {
            control.runExpanded(ugens)
            import control.config.executionContext
            val fut = control.status
            fut.andThen {
              case x => completeWith(x)
            }
          } catch {
            case NonFatal(ex) =>
              completeWith(Failure(ex))
          }
        }
        state_=(Rendering.Running(0.0), None)
      } catch {
        case NonFatal(ex) =>
          state_=(Rendering.Completed,  Some(Failure(ex)))
      }
    }

    def state(implicit tx: S#Tx): State = {
      import TxnLike.peer
      _state()
    }

    protected def state_=(value: Rendering.State, res: Option[Try[Unit]])(implicit tx: S#Tx): Unit = {
      import TxnLike.peer
      val old     = _state .swap(value)
      val oldRes  = _result.swap(res)
      if (old != value || (value == Rendering.Completed && oldRes != res)) fire(value)
    }

    def cancel()(implicit tx: S#Tx): Unit =
      tx.afterCommit(control.cancel())

    def dispose()(implicit tx: S#Tx): Unit = {
      cancel()
    }
  }

  private sealed trait Base[S <: Sys[S]] {
    _: FScape[S] =>

    final def tpe: Obj.Type = FScape

    override def toString: String = s"FScape$id"

    // --- rendering ---

    final def run(config: Control.Config)(implicit tx: S#Tx, cursor: stm.Cursor[S],
                                          workspace: WorkspaceHandle[S]): Rendering[S] = {
      val g = graph().value
      val r = new RenderingImpl[S](config)
      r.start(this, g)
      r
    }
  }

  private sealed trait ImplOLD[S <: Sys[S]]
    extends FScape[S] with Base[S] with evt.impl.SingleNode[S, FScape.Update[S]] {
    proc =>

    def copy[Out <: Sys[Out]]()(implicit tx: S#Tx, txOut: Out#Tx, context: Copy[S, Out]): Elem[Out] =
      new Impl[Out] { out =>
        protected val targets: Targets[Out] = Targets[Out]
        val graph     : GraphObj.Var[Out]                       = context(proc.graph)
        val outputsMap: SkipList.Map[Out, String, Output[Out]]  = SkipList.Map.empty
        connect()
      }

    import FScape._

    // ---- key maps ----

    def isConnected(implicit tx: S#Tx): Boolean = targets.nonEmpty

    final def connect()(implicit tx: S#Tx): this.type = {
      graph.changed ---> changed
      this
    }

    private def disconnect()(implicit tx: S#Tx): Unit = {
      graph.changed -/-> changed
    }

    object changed extends Changed
      with evt.impl.Generator[S, FScape.Update[S]] {
      def pullUpdate(pull: evt.Pull[S])(implicit tx: S#Tx): Option[FScape.Update[S]] = {
        val graphCh     = graph.changed
        val graphOpt    = if (pull.contains(graphCh)) pull(graphCh) else None
        val stateOpt    = Option.empty[FScape.Update[S]] // if (pull.isOrigin(this)) Some(pull.resolve[FScape.Update[S]]) else None

        val seq0 = graphOpt.fold(Vec.empty[Change[S]]) { u =>
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

    final protected def disposeData()(implicit tx: S#Tx): Unit = {
      disconnect()
      graph.dispose()
    }

    // dummy
    object outputs extends Outputs[S] {
      def get(key: String)(implicit tx: S#Tx): Option[Output[S]] = None

      def keys(implicit tx: S#Tx): Set[String] = Set.empty

      def iterator(implicit tx: S#Tx): Iterator[Output[S]] = Iterator.empty

      def add(key: String, tpe: Obj.Type)(implicit tx: S#Tx): Output[S] =
        throw new UnsupportedOperationException(s"Old FScape object without actual outputs, cannot call `add`")

      def remove(key: String)(implicit tx: S#Tx): Boolean = false
    }
  }

  private sealed trait Impl[S <: Sys[S]]
    extends FScape[S] with Base[S] with evt.impl.SingleNode[S, FScape.Update[S]] {
    proc =>

    // --- abstract ----

    protected def outputsMap: SkipList.Map[S, String, Output[S]]

    // --- impl ----

    def copy[Out <: Sys[Out]]()(implicit tx: S#Tx, txOut: Out#Tx, context: Copy[S, Out]): Elem[Out] =
      new Impl[Out] { out =>
        protected val targets: Targets[Out]                     = Targets[Out]
        val graph     : GraphObj.Var[Out]                       = context(proc.graph)
        val outputsMap: SkipList.Map[Out, String, Output[Out]]  = SkipList.Map.empty

        context.defer(proc, out) {
          def copyMap(in : SkipList.Map[S  , String, Output[S  ]],
                      out: SkipList.Map[Out, String, Output[Out]]): Unit =
            in.iterator.foreach { case (key, eIn) =>
              val eOut = context(eIn)
              out.add(key -> eOut)
            }

          // copyMap(proc.scanInMap , out.scanInMap)
          copyMap(proc.outputsMap, out.outputsMap)
        }
        connect()
      }

    import FScape._

    // ---- key maps ----

    def isConnected(implicit tx: S#Tx): Boolean = targets.nonEmpty

    final def connect()(implicit tx: S#Tx): this.type = {
      graph.changed ---> changed
      this
    }

    private def disconnect()(implicit tx: S#Tx): Unit = {
      graph.changed -/-> changed
    }

    object changed extends Changed
      with evt.impl.Generator[S, FScape.Update[S]] {
      def pullUpdate(pull: evt.Pull[S])(implicit tx: S#Tx): Option[FScape.Update[S]] = {
        val graphCh     = graph.changed
        val graphOpt    = if (pull.contains(graphCh)) pull(graphCh) else None
        val stateOpt    = if (pull.isOrigin(this)) Some(pull.resolve[FScape.Update[S]]) else None

        val seq0 = graphOpt.fold(Vec.empty[Change[S]]) { u =>
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

    final protected def disposeData()(implicit tx: S#Tx): Unit = {
      disconnect()
      graph     .dispose()
      outputsMap.dispose()
    }

    object outputs extends Outputs[S] {
      protected def fire(added: Option[Output[S]], removed: Option[Output[S]])
                        (implicit tx: S#Tx): Unit = {
        val b = Vector.newBuilder[FScape.OutputsChange[S]]
        b.sizeHint(2)
        // convention: first the removals, then the additions. thus, overwriting a key yields
        // successive removal and addition of the same key.
        removed.foreach { output =>
          b += FScape.OutputRemoved[S](output)
        }
        added.foreach { output =>
          b += FScape.OutputAdded  [S](output)
        }

        proc.changed.fire(FScape.Update(proc, b.result()))
      }

      private def add(key: String, value: Output[S])(implicit tx: S#Tx): Unit = {
        val optRemoved = outputsMap.add(key -> value)
        fire(added = Some(value), removed = optRemoved)
      }

      def remove(key: String)(implicit tx: S#Tx): Boolean =
        outputsMap.remove(key).exists { output =>
          fire(added = None, removed = Some(output))
          true
        }

      def add(key: String, tpe: Obj.Type)(implicit tx: S#Tx): Output[S] =
        get(key).getOrElse {
          val res = OutputImpl[S](proc, key, tpe)
          add(key, res)
          res
        }

      def get(key: String)(implicit tx: S#Tx): Option[Output[S]] = outputsMap.get(key)

      def keys(implicit tx: S#Tx): Set[String] = outputsMap.keysIterator.toSet

      def iterator(implicit tx: S#Tx): Iterator[Output[S]] = outputsMap.iterator.map(_._2)
    }
  }

  private final class New[S <: Sys[S]](implicit tx0: S#Tx) extends Impl[S] {
    protected val targets: Targets[S] = Targets(tx0)
    val graph     : GraphObj.Var[S]                     = GraphObj.newVar(GraphObj.empty)
    val outputsMap: SkipList.Map[S, String, Output[S]]  = SkipList.Map.empty
    connect()(tx0)
  }

  private final class ReadOLD[S <: Sys[S]](in: DataInput, access: S#Acc, protected val targets: Targets[S])
                                          (implicit tx0: S#Tx)
    extends ImplOLD[S] {

    val graph: GraphObj.Var[S] = GraphObj.readVar(in, access)
  }

  private final class Read[S <: Sys[S]](in: DataInput, access: S#Acc, protected val targets: Targets[S])
                                       (implicit tx0: S#Tx)
    extends Impl[S] {

    val graph     : GraphObj.Var[S]                     = GraphObj    .readVar(in, access)
    val outputsMap: SkipList.Map[S, String, Output[S]]  = SkipList.Map.read   (in, access)
  }
}