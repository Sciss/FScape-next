/*
 *  FScapeView.scala
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

package de.sciss.fscape.lucre.impl

import de.sciss.file._
import de.sciss.filecache
import de.sciss.filecache.TxnProducer
import de.sciss.fscape.lucre.FScape.Rendering
import de.sciss.fscape.lucre.FScape.Rendering.State
import de.sciss.fscape.lucre.UGenGraphBuilder.{MissingIn, OutputResult}
import de.sciss.fscape.lucre.{Cache, FScape, OutputGenView, UGenGraphBuilder}
import de.sciss.fscape.stream.Control
import de.sciss.lucre.event.impl.{DummyObservableImpl, ObservableImpl}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj, Sys, TxnLike}
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.serial.{DataInput, DataOutput, ImmutableSerializer}
import de.sciss.synth.proc.{GenView, Universe}

import scala.concurrent.stm.{Ref, TMap, atomic}
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object RenderingImpl {
  var DEBUG = false

  /** Creates a rendering with the default `UGenGraphBuilder.Context`.
    *
    * @param fscape     the fscape object whose graph is to be rendered
    * @param config     configuration for the stream control
    * @param force      if `true`, always renders even if there are no
    *                   outputs.
    */
  def apply[S <: SSys[S]](fscape: FScape[S], config: Control.Config, force: Boolean)
                        (implicit tx: S#Tx, universe: Universe[S]): Rendering[S] = {
    val ugbCtx = new UGenGraphBuilderContextImpl.Default(fscape)
    apply(fscape, ugbCtx, config, force = force)
  }

//  /** Creates a rendering with the custom `UGenGraphBuilder.Context`.
//    *
//    * @param g          the graph that is to be rendered
//    * @param ugbContext the graph builder context that responds to input requests
//    * @param config     configuration for the stream control
//    */
//  def apply[S <: Sys[S]](g: Graph, ugbContext: UGenGraphBuilder.Context[S], config: Control.Config)
//                        (implicit tx: S#Tx, context: GenContext[S]): Rendering[S] = {
//    import context.{cursor, workspaceHandle}
//    implicit val control: Control = Control(config)
//    val uState = UGenGraphBuilder.build(ugbContext, g)
//    withState(uState, force = true)
//  }

  /** Creates a rendering with the custom `UGenGraphBuilder.Context`.
    *
    * @param fscape     the fscape object whose graph is to be rendered
    * @param ugbContext the graph builder context that responds to input requests
    * @param config     configuration for the stream control
    * @param force      if `true`, always renders even if there are no
    *                   outputs.
    */
  def apply[S <: Sys[S]](fscape: FScape[S], ugbContext: UGenGraphBuilder.Context[S], config: Control.Config,
                         force: Boolean)
                       (implicit tx: S#Tx, universe: Universe[S]): Rendering[S] = {
    implicit val control: Control = Control(config)
    import universe.{cursor, workspace}
    val uState = UGenGraphBuilder.build(ugbContext, fscape)
    withState(uState, force = force)
  }

  trait WithState[S <: Sys[S]] extends Rendering[S] {
    def cacheResult(implicit tx: S#Tx): Option[Try[CacheValue]]
  }

  /** Turns a built UGen graph into a rendering instance.
    *
    * @param uState   the result of building, either complete or incomplete
    * @param force    if `true` forces rendering of graphs that do not produce outputs
    *
    * @return a rendering, either cached, or newly started, or directly aborted if the graph was incomplete
    */
  def withState[S <: Sys[S]](uState: UGenGraphBuilder.State[S], force: Boolean)
                            (implicit tx: S#Tx, control: Control, cursor: stm.Cursor[S]): WithState[S] =
    uState match {
      case res: UGenGraphBuilder.Complete[S] =>
        val isEmpty = res.outputs.isEmpty
        // - if there are no outputs, we're done
        if (isEmpty && !force) {
          new EmptyImpl[S](control)
        } else {
          // - otherwise check structure:
          val struct = res.structure
          import control.config.executionContext

          def mkFuture(): Future[CacheValue] = {
            val res0 = try {
              control.runExpanded(res.graph)
              val fut = control.status
              fut.map { _ =>
                val resourcesB  = List.newBuilder[File]
                val dataB       = Map .newBuilder[String, Array[Byte]]

                res.outputs.foreach { outRes =>
                  resourcesB ++= outRes.cacheFiles
                  val out = DataOutput()
                  outRes.writer.write(out)
                  val arr = out.toByteArray
                  dataB += outRes.key -> arr
                }

                val resources = resourcesB.result()
                val data      = dataB     .result()
                new CacheValue(resources, data)
              }
            } catch {
              case NonFatal(ex) =>
                Future.failed(ex)
            }

            // require(Txn.findCurrent.isEmpty, "IN TXN")
            // if (DEBUG) res0.onComplete(x => println(s"Rendering future early observation: $x"))
            res0
          }

          val useCache = !isEmpty
          val fut: Future[CacheValue] = if (useCache) {
            // - check file cache for structure
            RenderingImpl.acquire[S](struct)(mkFuture())
          } else {
            val p = Promise[CacheValue]()
            tx.afterCommit {
              val _fut = mkFuture()
              p.completeWith(_fut)
            }
            p.future
          }

          val impl = new Impl[S](struct, res.outputs, control, fut, useCache = useCache)
          fut.onComplete { cvt =>
            if (DEBUG) println(s"$impl completeWith $cvt")
            impl.completeWith(cvt)
          }
          impl
        }

      case res =>
        new FailedImpl[S](control, res.rejectedInputs)
    }

  type CacheKey = Long

  object CacheValue {
    private[this] val COOKIE = 0x46734356   // "FsCV"

    implicit object serializer extends ImmutableSerializer[CacheValue] {
      def read(in: DataInput): CacheValue = {
        val cookie = in.readInt()
        if (cookie != COOKIE) sys.error(s"Unexpected cookie (found $cookie, expected $COOKIE)")
        val numFiles  = in.readUnsignedShort()
        val resources = if (numFiles == 0) Nil else List.fill(numFiles)(new File(in.readUTF()))
        val numData   = in.readShort()
        val data: Map[String, Array[Byte]] = if (numData == 0) Map.empty else {
          val b     = Map.newBuilder[String, Array[Byte]]
          b.sizeHint(numData)
          var i     = 0
          while (i < numData) {
            val key   = in.readUTF()
            val sz    = in.readUnsignedShort()
            val data  = new Array[Byte](sz)
            in.readFully(data)
            b += key -> data
            i += 1
          }
          b.result()
        }
        new CacheValue(resources, data)
      }

      def write(v: CacheValue, out: DataOutput): Unit = {
        out.writeInt(COOKIE)
        val numFiles = v.resources.size
        out.writeShort(numFiles)
        if (numFiles > 0) v.resources.foreach(f => out.writeUTF(f.path))
        val numData = v.data.size
        out.writeShort(numData)
        if (numData > 0) v.data.foreach { case (key, data) =>
          out.writeUTF(key)
          out.writeShort(data.length)
          out.write(data)
        }
      }
    }
  }
  final class CacheValue(val resources: List[File], val data: Map[String, Array[Byte]])

  private[this] lazy val producer: TxnProducer[CacheKey, CacheValue] = {
    val cacheCfg = filecache.Config[CacheKey, CacheValue]()
    val global   = Cache.instance
    cacheCfg.accept           = { (_ /* key */, _ /* value */) => true }
    cacheCfg.space            = { (_ /* key */, value) => value.resources.map    (_.length()).sum }
    cacheCfg.evict            = { (_ /* key */, value) => value.resources.foreach(_.delete())     }
    cacheCfg.capacity         = global.capacity
    cacheCfg.executionContext = global.executionContext
    cacheCfg.extension        = global.extension
    cacheCfg.folder           = global.folder
    atomic { implicit tx => TxnProducer(cacheCfg) }
  }

  // same as filecache.impl.TxnConsumerImpl.Entry
  private final class Entry[B](val useCount: Int = 1, val future: Future[B]) {
    def inc = new Entry(useCount + 1, future)
    def dec = new Entry(useCount - 1, future)
  }

  private[this] val map = TMap.empty[CacheKey, Entry[CacheValue]]

  // mostly same as filecache.impl.TxnConsumerImpl.acquire
  def acquire[S <: Sys[S]](key: CacheKey)(source: => Future[CacheValue])
                          (implicit tx: S#Tx)      : Future[CacheValue] = {
    import TxnLike.peer
    map.get(key).fold {
      val fut = producer.acquireWith(key)(source)
      val e = new Entry(future = fut)
      map.put(key, e)
      import producer.executionContext
      fut.recover {
        case NonFatal(t) =>
          map.single.remove(key)
          throw t
      }
      fut
    } { e0 =>
      val e1 = e0.inc
      map.put(key, e1)
      e1.future
    }
  }

  // mostly same as filecache.impl.TxnConsumerImpl.release
  def release[S <: Sys[S]](key: CacheKey)(implicit tx: S#Tx): Boolean = {
    import TxnLike.peer
    map.get(key) match {
      case Some(e0) =>
        val e1    = e0.dec
        val last  = e1.useCount == 0
        if (last) {
          map.remove(key)
          producer.release(key)
        } else {
          map.put(key, e1)
        }
        last
      case None =>
        // throw new IllegalStateException(s"Key $key was not in use")
        tx.afterCommit {
          Console.err.println(s"Warning: fscape.Rendering: Key $key was not in use.")
        }
        false
    }
  }

  // FScape is rendering
  private final class Impl[S <: Sys[S]](struct: CacheKey, outputs: List[OutputResult[S]],
                                        val control: Control, fut: Future[CacheValue],
                                        useCache: Boolean)(implicit cursor: stm.Cursor[S])
    extends Basic[S] with ObservableImpl[S, GenView.State] {

    override def toString = s"Impl@${hashCode.toHexString} - ${fut.value}"

    private[this] val _disposed = Ref(false)
    private[this] val _state    = Ref[GenView.State](if (fut.isCompleted) GenView.Completed else GenView.Running(0.0))
    private[this] val _result   = Ref[Option[Try[CacheValue]]](fut.value)

    def result(implicit tx: S#Tx): Option[Try[Unit]] = _result.get(tx.peer).map(_.map(_ => ()))

    def cacheResult(implicit tx: S#Tx): Option[Try[CacheValue]] = _result.get(tx.peer)

    def outputResult(outputView: OutputGenView[S])(implicit tx: S#Tx): Option[Try[Obj[S]]] = {
      _result.get(tx.peer) match {
        case Some(Success(cv)) =>
          outputView.output match {
            case oi: OutputImpl[S] =>
              val valOpt: Option[Obj[S]] = oi.value.orElse {
                val key = oi.key // outputView.key
                outputs.find(_.key == key).flatMap { outRef =>
                  val in = DataInput(cv.data(key))
                  outRef.updateValue(in)
                  oi.value
                }
              }
              valOpt.map(v => Success(v))

            case _ => None
          }
        case res @ Some(Failure(_)) =>
          res.asInstanceOf[Option[Try[Obj[S]]]]

        case None => None
      }
    }

    def completeWith(t: Try[CacheValue]): Unit =
      if (!_disposed.single.get)
        cursor.step { implicit tx =>
          import TxnLike.peer
          if (!_disposed()) {
            // update first...
            if (t.isSuccess && outputs.nonEmpty) t.foreach { cv =>
              outputs.foreach { outRef =>
                val in = DataInput(cv.data(outRef.key))
                outRef.updateValue(in)
              }
            }
            _state .set(GenView.Completed)(tx.peer)
            _result.set(fut.value)(tx.peer)
            // ...then issue event
            fire(GenView.Completed)
          }
        }

    def state(implicit tx: S#Tx): GenView.State = _state.get(tx.peer)

    def dispose()(implicit tx: S#Tx): Unit =    // XXX TODO --- should cancel processor
      if (!_disposed.swap(true)(tx.peer)) {
        if (useCache) RenderingImpl.release[S](struct)
        cancel()
      }
  }

  private sealed trait Basic[S <: Sys[S]] extends WithState[S] {
    def reactNow(fun: S#Tx => State => Unit)(implicit tx: S#Tx): Disposable[S#Tx] = {
      val res = react(fun)
      fun(tx)(state)
      res
    }

    def cancel()(implicit tx: S#Tx): Unit =
      tx.afterCommit(control.cancel())
  }

  private sealed trait DummyImpl[S <: Sys[S]] extends Basic[S]
    with DummyObservableImpl[S] {

    final def state(implicit tx: S#Tx): GenView.State = GenView.Completed

    final def dispose()(implicit tx: S#Tx): Unit = ()
  }

  // FScape does not provide outputs, nothing to do
  private final class EmptyImpl[S <: Sys[S]](val control: Control) extends DummyImpl[S] {
    def result(implicit tx: S#Tx): Option[Try[Unit]] = Some(Success(()))

    def outputResult(output: OutputGenView[S])(implicit tx: S#Tx): Option[Try[Obj[S]]] = None

    def cacheResult(implicit tx: S#Tx): Option[Try[CacheValue]] =
      Some(Success(new CacheValue(Nil, Map.empty)))
  }

  // FScape failed early (e.g. graph inputs incomplete)
  private final class FailedImpl[S <: Sys[S]](val control: Control, rejected: Set[String]) extends DummyImpl[S] {
    def result(implicit tx: S#Tx): Option[Try[Unit]] = nada

    def outputResult(output: OutputGenView[S])(implicit tx: S#Tx): Option[Try[Obj[S]]] = nada

    private def nada: Option[Try[Nothing]] = Some(Failure(MissingIn(rejected.head)))

    def cacheResult(implicit tx: S#Tx): Option[Try[CacheValue]] = nada
  }
}