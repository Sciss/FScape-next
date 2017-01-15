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

import de.sciss.filecache
import de.sciss.filecache.TxnProducer
import de.sciss.fscape.stream.Control
import de.sciss.lucre.event.Observable
import de.sciss.lucre.event.impl.{DummyObservableImpl, ObservableImpl}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Sys, TxnLike}
import de.sciss.serial.{DataInput, DataOutput, ImmutableSerializer}
import de.sciss.synth.proc.{GenContext, GenView}

import scala.concurrent.Future
import scala.concurrent.stm.{Ref, TMap, atomic}
import scala.util.control.NonFatal
import scala.util.{Success, Try}

object FScapeView {
  def apply[S <: Sys[S]](peer: FScape[S], config: Control.Config)
                        (implicit tx: S#Tx, context: GenContext[S]): FScapeView[S] = {
    val graph = peer.graph.value
    import context.{cursor, workspaceHandle}
    implicit val control: Control = Control(config)
    val uState  = UGenGraphBuilder.build(peer, graph)
    val fH      = tx.newHandle(peer)
    uState match {
      case res: UGenGraphBuilder.Complete[S] =>
        // - if there are no outputs, we're done
        if (res.outputs.isEmpty) {
          new EmptyImpl[S]
        } else {
          // - otherwise check structure:
          val key = res.structure
          // - check file cache for structure
          import control.config.executionContext
          val fut: Future[CacheValue] = FScapeView.acquire[S](key) {
            try {
              control.runExpanded(res.graph)
              val fut = control.status
              fut.map { _ =>
                // case x => completeWith(x, res.outputs, fH)
                println("Yo Chuck! continue here!")
                new CacheValue {}
              }
            } catch {
              case NonFatal(ex) =>
                Future.failed(ex)
            }
          }
          val impl = new Impl[S](key, fut)
          fut.onComplete {
            res => impl.completeWith(res)
          }
          impl
        }

      case res =>
        new FailedImpl[S](res.rejectedInputs)
    }
  }

  private val successUnit = Success(())

  // private final class CacheKey(c: UGenGraphBuilder.Complete[S])

  private type CacheKey = Long

  private object CacheValue {
    implicit object serializer extends ImmutableSerializer[CacheValue] {
      def read(in: DataInput): CacheValue = new CacheValue {}

      def write(v: CacheValue, out: DataOutput): Unit = ()
    }
  }
  private trait CacheValue

  private[this] val producer: TxnProducer[CacheKey, CacheValue] = {
    val cacheCfg = filecache.Config[CacheKey, CacheValue]()
    //    cacheCfg.accept
    //    cacheCfg.capacity
    //    cacheCfg.evict
    //    cacheCfg.executionContext
    //    cacheCfg.extension
    //    cacheCfg.folder
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
                                  (implicit tx: S#Tx): Future[CacheValue] = {
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
  private final class Impl[S <: Sys[S]](key: CacheKey, fut: Future[CacheValue])(implicit cursor: stm.Cursor[S])
    extends FScapeView[S] with ObservableImpl[S, GenView.State] {

    private[this] val _disposed = Ref(false)
    private[this] val _state    = Ref[GenView.State](if (fut.isCompleted) GenView.Completed else GenView.Running(0.0))

    def completeWith(t: Try[CacheValue]): Unit =
      if (!_disposed.single.get)
        cursor.step { implicit tx =>
          import TxnLike.peer
          if (!_disposed()) {
            state_=(GenView.Completed) // (Rendering.Completed, Some(t))
//            if (t.isSuccess && outputMap.nonEmpty) {
//              outputMap.foreach { case (key, (valueType, outRef)) =>
//                if (outRef.hasProvider) {
//                  // val v = outRef.mkValue()
//                  outRef.updateValue()
//                }
//              }
//            }
          }
        }


    def state(implicit tx: S#Tx): GenView.State = _state.get(tx.peer)

    private def state_=(value: GenView.State)(implicit tx: S#Tx): Unit = {
      val old = _state.swap(value)(tx.peer)
      if (value != old) fire(value)
    }

    def dispose()(implicit tx: S#Tx): Unit =    // XXX TODO --- should cancel processor
      if (!_disposed.swap(true)(tx.peer))
        FScapeView.release[S](key)
  }

  private sealed trait DummyImpl[S <: Sys[S]] extends FScapeView[S]
    with DummyObservableImpl[S] {

    final def state(implicit tx: S#Tx): GenView.State = GenView.Completed

    final def dispose()(implicit tx: S#Tx): Unit = ()
  }

  // FScape does not provide outputs, nothing to do
  private final class EmptyImpl[S <: Sys[S]] extends DummyImpl[S]

  // FScape failed early (e.g. graph inputs incomplete)
  private final class FailedImpl[S <: Sys[S]](rejected: Set[String]) extends DummyImpl[S]
}

trait FScapeView[S <: Sys[S]] extends Observable[S#Tx, GenView.State] with Disposable[S#Tx] {
  def state(implicit tx: S#Tx): GenView.State
}