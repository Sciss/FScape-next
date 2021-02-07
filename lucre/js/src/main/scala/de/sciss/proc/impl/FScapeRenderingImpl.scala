/*
 *  FScapeRenderingImpl.scala
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

import de.sciss.fscape.lucre.UGenGraphBuilder
import de.sciss.fscape.lucre.UGenGraphBuilder.{MissingIn, OutputResult}
import de.sciss.fscape.lucre.impl.UGenGraphBuilderContextImpl
import de.sciss.fscape.stream.Control
import de.sciss.lucre.impl.{DummyObservableImpl, ObservableImpl}
import de.sciss.lucre.synth.{Txn => STxn}
import de.sciss.lucre.{Cursor, Disposable, Obj, Txn}
import de.sciss.proc.FScape.Rendering.State
import de.sciss.proc.FScape.{Output, Rendering}
import de.sciss.proc.{FScape, GenView, Runner, SoundProcesses, Universe}
import de.sciss.serial.{DataInput, DataOutput}

import scala.concurrent.stm.Ref
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object FScapeRenderingImpl {
  var DEBUG = false

  /** Creates a rendering with the default `UGenGraphBuilder.Context`.
    *
    * @param fscape the fscape object whose graph is to be rendered
    * @param config configuration for the stream control
    * @param force  if `true`, always renders even if there are no
    *               outputs.
    */
  def apply[T <: STxn[T]](fscape: FScape[T], config: Control.Config, attr: Runner.Attr[T], force: Boolean)
                         (implicit tx: T, universe: Universe[T]): Rendering[T] = {
    val ugbCtx = new UGenGraphBuilderContextImpl.Default(fscape, attr = attr)
    apply(fscape, ugbCtx, config, force = force)
  }

  /** Creates a rendering with the custom `UGenGraphBuilder.Context`.
    *
    * @param fscape     the fscape object whose graph is to be rendered
    * @param ugbContext the graph builder context that responds to input requests
    * @param config     configuration for the stream control
    * @param force      if `true`, always renders even if there are no
    *                   outputs.
    */
  def apply[T <: Txn[T]](fscape: FScape[T], ugbContext: UGenGraphBuilder.Context[T], config: Control.Config,
                         force: Boolean)
                        (implicit tx: T, universe: Universe[T]): Rendering[T] = {
    implicit val control: Control = Control(config)
    import universe.cursor
    val uState = UGenGraphBuilder.build(ugbContext, fscape)
    withState(uState, force = force)
  }

  trait WithState[T <: Txn[T]] extends Rendering[T] { // Used by TxnSon
    def cacheResult(implicit tx: T): Option[Try[CacheValue]]
  }

  /** Turns a built UGen graph into a rendering instance. Used by TxnSon.
    *
    * @param uState the result of building, either complete or incomplete
    * @param force  if `true` forces rendering of graphs that do not produce outputs
    * @return a rendering, either cached, or newly started, or directly aborted if the graph was incomplete
    */
  def withState[T <: Txn[T]](uState: UGenGraphBuilder.State[T], force: Boolean)
                            (implicit tx: T, control: Control, cursor: Cursor[T]): WithState[T] =
    uState match {
      case res: UGenGraphBuilder.Complete[T] =>
        val isEmpty = res.outputs.isEmpty
        // - if there are no outputs, we're done
        if (isEmpty && !force) {
          new EmptyImpl[T](control)
        } else {
          // - otherwise check structure:
          val struct = res.structure
          import control.config.executionContext

          def mkFuture(): Future[CacheValue] = {
            val res0 = try {
              control.runExpanded(res.graph)
              val fut = control.status
              fut.map { _ =>
                //                val resourcesB  = List.newBuilder[File]
                val dataB = Map.newBuilder[String, Array[Byte]]

                res.outputs.foreach { outRes =>
                  //                  resourcesB ++= outRes.cacheFiles
                  val out = DataOutput()
                  val w = outRes.writer
                  w.write(out)
                  val bytes = out.toByteArray
                  // val data  = (w.outputValue, bytes)
                  dataB += outRes.key -> bytes
                }

                //                val resources = resourcesB.result()
                val data = dataB.result()
                new CacheValue(/*resources,*/ data)
              }
            } catch {
              case NonFatal(ex) =>
                Future.failed(ex)
            }

            // require(Txn.findCurrent.isEmpty, "IN TXN")
            // if (DEBUG) res0.onComplete(x => println(s"Rendering future early observation: $x"))
            res0
          }

          val useCache = !isEmpty && !force // new variant: `force` has to be `false` to use cache
          require(!useCache, "Cache not implemented in Scala.js")
          val fut: Future[CacheValue] =
          //            if (useCache) {
          //              // - check file cache for structure
          //              RenderingImpl.acquire[T](struct)(mkFuture())
          //            } else
          {
            val p = Promise[CacheValue]()
            tx.afterCommit {
              val _fut = mkFuture()
              p.completeWith(_fut)
            }
            p.future
          }

          val impl = new Impl[T](struct, res.outputs, control, fut, useCache = useCache)
          fut.onComplete { cvt =>
            if (DEBUG) println(s"$impl completeWith $cvt")
            impl.completeWith(cvt)
          }
          impl
        }

      case res =>
        new FailedImpl[T](control, res.rejectedInputs)
    }

  type CacheKey = Long

  final class CacheValue(val data: Map[String, Array[Byte]]) {
    override def toString: String = s"CacheValue@${hashCode().toHexString}"
  }

  //  // same as filecache.impl.TxnConsumerImpl.Entry
  //  private final class Entry[B](val useCount: Int = 1, val future: Future[B]) {
  //    def inc = new Entry(useCount + 1, future)
  //    def dec = new Entry(useCount - 1, future)
  //  }

  //  private[this] val map = TMap.empty[CacheKey, Entry[CacheValue]]

  // FScape is rendering
  private final class Impl[T <: Txn[T]](struct: CacheKey, outputs: List[OutputResult[T]],
                                        val control: Control, fut: Future[CacheValue],
                                        useCache: Boolean)(implicit cursor: Cursor[T])
    extends Basic[T] with ObservableImpl[T, GenView.State] {

    override def toString = s"Impl@${hashCode.toHexString} - ${fut.value}"

    private[this] val _disposed = Ref(false)
    private[this] val _state = Ref[GenView.State](if (fut.isCompleted) GenView.Completed else GenView.Running(0.0))
    private[this] val _result = Ref[Option[Try[CacheValue]]](fut.value)

    def result(implicit tx: T): Option[Try[Unit]] = _result.get(tx.peer).map(_.map(_ => ()))

    def cacheResult(implicit tx: T): Option[Try[CacheValue]] = _result.get(tx.peer)

    def outputResult(outputView: Output.GenView[T])(implicit tx: T): Option[Try[Obj[T]]] = {
      _result.get(tx.peer) match {
        case Some(Success(cv)) =>
          outputView.output match {
            case oi: FScapeOutputImpl[T] =>
              val valOpt: Option[Obj[T]] = oi.value.orElse {
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
        case res@Some(Failure(_)) =>
          res.asInstanceOf[Option[Try[Obj[T]]]]

        case None => None
      }
    }

    def completeWith(t: Try[CacheValue]): Unit =
      SoundProcesses.step[T]("FScape completeWith") { implicit tx =>
        import Txn.peer
        if (!_disposed()) {
          // update first...
          if (t.isSuccess && outputs.nonEmpty) t.foreach { cv =>
            outputs.foreach { outRef =>
              val in = DataInput(cv.data(outRef.key))
              outRef.updateValue(in)
            }
          }
          _state.set(GenView.Completed)(tx.peer)
          _result.set(fut.value)(tx.peer)
          // ...then issue event
          fire(GenView.Completed)
        }
      }

    def state(implicit tx: T): GenView.State = _state.get(tx.peer)

    def dispose()(implicit tx: T): Unit = // XXX TODO --- should cancel processor
      if (!_disposed.swap(true)(tx.peer)) {
        assert(!useCache)
        //        if (useCache) RenderingImpl.release[T](struct)
        cancel()
      }
  }

  private sealed trait Basic[T <: Txn[T]] extends WithState[T] {
    def reactNow(fun: T => State => Unit)(implicit tx: T): Disposable[T] = {
      val res = react(fun)
      fun(tx)(state)
      res
    }

    def cancel()(implicit tx: T): Unit =
      tx.afterCommit(control.cancel())
  }

  private sealed trait DummyImpl[T <: Txn[T]] extends Basic[T]
    with DummyObservableImpl[T] {

    final def state(implicit tx: T): GenView.State = GenView.Completed

    final def dispose()(implicit tx: T): Unit = ()
  }

  // FScape does not provide outputs, nothing to do
  private final class EmptyImpl[T <: Txn[T]](val control: Control) extends DummyImpl[T] {
    def result(implicit tx: T): Option[Try[Unit]] = Some(Success(()))

    def outputResult(output: Output.GenView[T])(implicit tx: T): Option[Try[Obj[T]]] = None

    def cacheResult(implicit tx: T): Option[Try[CacheValue]] =
      Some(Success(new CacheValue(/*Nil,*/ Map.empty)))
  }

  // FScape failed early (e.g. graph inputs incomplete)
  private final class FailedImpl[T <: Txn[T]](val control: Control, rejected: Set[String]) extends DummyImpl[T] {
    def result(implicit tx: T): Option[Try[Unit]] = nada

    def outputResult(output: Output.GenView[T])(implicit tx: T): Option[Try[Obj[T]]] = nada

    private def nada: Option[Try[Nothing]] = Some(Failure(MissingIn(rejected.head)))

    def cacheResult(implicit tx: T): Option[Try[CacheValue]] = nada
  }

}
