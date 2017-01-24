/*
 *  FScapeView.scala
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

import de.sciss.file._
import de.sciss.filecache
import de.sciss.filecache.TxnProducer
import de.sciss.fscape.lucre.FScape.Rendering
import de.sciss.fscape.lucre.FScape.Rendering.State
import de.sciss.fscape.lucre.UGenGraphBuilder.{MissingIn, OutputResult}
import de.sciss.fscape.stream.Control
import de.sciss.lucre.event.impl.{DummyObservableImpl, ObservableImpl}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj, Sys, TxnLike}
import de.sciss.serial.{DataInput, DataOutput, ImmutableSerializer}
import de.sciss.synth.proc.{GenContext, GenView}

import scala.concurrent.{Future, Promise}
import scala.concurrent.stm.{Ref, TMap, atomic}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object RenderingImpl {
  /** Creates a view with the default `UGenGraphBuilder.Context`.
    *
    * @param fscape     the fscape object whose graph is to be rendered
    * @param config     configuration for the stream control
    * @param force      if `true`, always renders even if there are no
    *                   outputs.
    */
  def apply[S <: Sys[S]](fscape: FScape[S], config: Control.Config, force: Boolean)
                        (implicit tx: S#Tx, context: GenContext[S]): Rendering[S] = {
    val ugbCtx = new UGenGraphBuilderContextImpl.Default(fscape)
    apply(fscape, ugbCtx, config, force = force)
  }

  /** Creates a view with the custom `UGenGraphBuilder.Context`.
    *
    * @param fscape     the fscape object whose graph is to be rendered
    * @param ugbContext the graph builder context that responds to input requests
    * @param config     configuration for the stream control
    * @param force      if `true`, always renders even if there are no
    */
  def apply[S <: Sys[S]](fscape: FScape[S], ugbContext: UGenGraphBuilder.Context[S], config: Control.Config,
                         force: Boolean)
                       (implicit tx: S#Tx, context: GenContext[S]): Rendering[S] = {
    import context.{cursor, workspaceHandle}
    implicit val control: Control = Control(config)
    val ugbCtx = new UGenGraphBuilderContextImpl.Default(fscape)
    val uState = UGenGraphBuilder.build(ugbCtx, fscape)
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

          def mkFuture(): Future[CacheValue] =
            try {
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
          fut.onComplete {
            cvt => impl.completeWith(cvt)
          }
          impl
        }

      case res =>
        new FailedImpl[S](control, res.rejectedInputs)
    }
  }

  private type CacheKey = Long

  private object CacheValue {
//    private[this] val resourcesSer = ImmutableSerializer.list[File]
//    private[this] val dataSer      = ImmutableSerializer.map[String, Array[Byte]]

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
  private final class CacheValue(val resources: List[File], val data: Map[String, Array[Byte]])

  private[this] lazy val producer: TxnProducer[CacheKey, CacheValue] = {
    val cacheCfg = filecache.Config[CacheKey, CacheValue]()
    val global   = Cache.instance
    cacheCfg.accept           = { (key, value) => true }
    cacheCfg.space            = { (key, value) => value.resources.map    (_.length()).sum }
    cacheCfg.evict            = { (key, value) => value.resources.foreach(_.delete())     }
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
  private def acquire[S <: Sys[S]](key: CacheKey)(source: => Future[CacheValue])
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
  private def release[S <: Sys[S]](key: CacheKey)(implicit tx: S#Tx): Boolean = {
    import TxnLike.peer
    val e0    = map.get(key).getOrElse(throw new IllegalStateException(s"Key $key was not in use"))
    val e1    = e0.dec
    val last  = e1.useCount == 0
    if (last) {
      map.remove(key)
      producer.release(key)
    } else {
      map.put(key, e1)
    }
    last
  }

  // FScape is rendering
  private final class Impl[S <: Sys[S]](struct: CacheKey, outputs: List[OutputResult[S]],
                                        val control: Control, fut: Future[CacheValue],
                                        useCache: Boolean)(implicit cursor: stm.Cursor[S])
    extends Basic[S] with ObservableImpl[S, GenView.State] {

    private[this] val _disposed = Ref(false)
    private[this] val _state    = Ref[GenView.State](if (fut.isCompleted) GenView.Completed else GenView.Running(0.0))
    private[this] val _result   = Ref[Option[Try[CacheValue]]](fut.value)

    def result(implicit tx: S#Tx): Option[Try[Unit]] = _result.get(tx.peer).map(_.map(_ => ()))

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

  private sealed trait Basic[S <: Sys[S]] extends Rendering[S] {
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
  }

  // FScape failed early (e.g. graph inputs incomplete)
  private final class FailedImpl[S <: Sys[S]](val control: Control, rejected: Set[String]) extends DummyImpl[S] {
    def result(implicit tx: S#Tx): Option[Try[Unit]] = nada

    def outputResult(output: OutputGenView[S])(implicit tx: S#Tx): Option[Try[Obj[S]]] = nada

    private def nada: Option[Try[Nothing]] = Some(Failure(MissingIn(rejected.head)))
  }
}
//
//trait FScapeView[S <: Sys[S]] extends Observable[S#Tx, GenView.State] with Disposable[S#Tx] {
//  def state(implicit tx: S#Tx): GenView.State
//
//  def result(output: OutputGenView[S])(implicit tx: S#Tx): Option[Try[Obj[S]]]
//}