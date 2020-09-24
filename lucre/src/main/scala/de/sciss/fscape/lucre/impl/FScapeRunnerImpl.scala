/*
 *  FScapeRunnerImpl.scala
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

package de.sciss.fscape.lucre
package impl

import java.awt.event.{ActionEvent, ActionListener}

import de.sciss.fscape.lucre.FScape.Rendering
import de.sciss.fscape.stream.Control
import de.sciss.lucre.impl.ObservableImpl
import de.sciss.lucre.Txn.peer
import de.sciss.lucre.{Disposable, Obj, Source, Txn, synth}
import de.sciss.synth.proc.Runner.{Done, Failed, Stopped}
import de.sciss.synth.proc.impl.BasicRunnerImpl
import de.sciss.synth.proc.{Runner, SoundProcesses, Universe}

import scala.concurrent.ExecutionException
import scala.concurrent.stm.Ref
import scala.util.{Failure, Success}

object FScapeRunnerImpl extends Runner.Factory {
  var DEBUG_USE_ASYNC = false // warning: Akka uses completely intrusive behaviour when in async

  final val prefix          = "FScape"
  def humanName : String    = prefix
  def tpe       : Obj.Type  = FScape

  type Repr[~ <: Txn[~]] = FScape[~]

  def isSingleton: Boolean = false  // hmmm, this depends?

  private lazy val _init: Unit = Runner.addFactory(this)

  def init(): Unit = _init

  def mkRunner[T <: synth.Txn[T]](obj: FScape[T])(implicit tx: T, universe: Universe[T]): Runner[T] =
    new Impl(tx.newHandle(obj))

  private final class Impl[T <: synth.Txn[T]](val objH: Source[T, FScape[T]])
                                             (implicit val universe: Universe[T])
    extends BasicRunnerImpl[T] /*with ObjViewBase[T, Unit]*/ {

    private[this] val renderRef = Ref(Option.empty[Rendering[T]])
    private[this] val obsRef    = Ref(Disposable.empty[T])
    private[this] val attrRef   = Ref(Runner.emptyAttr[T])(NoManifest)

    def tpe: Obj.Type = FScape

    object progress extends Runner.Progress[T] with ObservableImpl[T, Double] with ActionListener {
      private[this] val ref = Ref(-1.0)

      def current(implicit tx: T): Double = ref()

      def current_=(value: Double)(implicit tx: T): Unit = {
        val old = ref.swap(value)
        if (value != old) fire(value)
      }

      // XXX TODO --- introduce a general abstraction for throttling

      private[this] var guiValue = -1.0

      private[this] lazy val timer = {
        val t = new javax.swing.Timer(200, this)
        t.setRepeats(false)
        t
      }

      private[this] var lastReported = 0L

      def push(value: Double): Unit = {
        guiValue = value
        val now = System.currentTimeMillis()
        if (now - lastReported > 250) {
          timer.stop()
          actionPerformed(null)
        } else if (!timer.isRunning) {
          timer.restart()
        }
      }

      def actionPerformed(e: ActionEvent): Unit = {
        lastReported = System.currentTimeMillis()
        SoundProcesses.step[T]("FScape Runner progress") { implicit tx =>
          current = guiValue
        }
      }
    }

    protected def disposeData()(implicit tx: T): Unit = {
      obsRef   .swap(Disposable.empty).dispose()
      disposeRender()
    }

    private def disposeRender()(implicit tx: T): Unit = {
      renderRef.swap(None).foreach  (_.dispose())
//      attrRef.swap(Runner.emptyAttr[T]).dispose()
      attrRef() = Runner.emptyAttr[T]
    }

    def prepare(attr: Runner.Attr[T])(implicit tx: T): Unit = {
      attrRef() = attr
      state     = Runner.Prepared
    }

    def run()(implicit tx: T): Unit = {
      val obj = objH()
      messages.current = Nil
      state = Runner.Running
      val cfg = Control.Config()
      cfg.progressReporter = { p =>
        progress.push(p.total)
      }
      cfg.useAsync  = DEBUG_USE_ASYNC
      val attr      = attrRef()
      val r: Rendering[T] = obj.run(cfg, attr)
      renderRef.swap(Some(r)).foreach(_.dispose())
      obsRef   .swap(Disposable.empty) .dispose()
      val newObs = r.reactNow { implicit tx => {
        case Rendering.Completed =>
          val res = r.result
          disposeRender()
          state = res match {
            case Some(Failure(ex0)) =>
              val ex    = ex0 match {
                case cc: ExecutionException => cc.getCause
                case _                      => ex0
              }
//              val clz   = ex.getClass.getName
//              val mTxt  = if (ex.getMessage == null) clz else s"$clz - ${ex.getMessage}"
              val mTxt  = ex.toString
              val m     = Runner.Message(System.currentTimeMillis(), Runner.Message.Error, mTxt)
              messages.current = m :: Nil
              Failed(ex)

            case Some(Success(_)) => Done

            case _ => Stopped
          }

        case _ =>
      }}
      obsRef() = newObs
    }

    def stop()(implicit tx: T): Unit = {
      disposeRender()
      state = Runner.Stopped
    }
  }
}
