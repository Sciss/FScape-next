/*
 *  FScapeRunnerImpl.scala
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

package de.sciss.fscape.lucre
package impl

import java.awt.event.{ActionEvent, ActionListener}

import de.sciss.fscape.lucre.FScape.Rendering
import de.sciss.fscape.stream.Control
import de.sciss.lucre.event.impl.ObservableImpl
import de.sciss.lucre.stm.TxnLike.peer
import de.sciss.lucre.stm.{Disposable, Obj, Sys}
import de.sciss.lucre.{stm, synth}
import de.sciss.synth.proc.impl.BasicRunnerImpl
import de.sciss.synth.proc.{ObjViewBase, Runner, TimeRef}

import scala.concurrent.ExecutionException
import scala.concurrent.stm.Ref
import scala.util.Failure

object FScapeRunnerImpl extends Runner.Factory {
  var DEBUG_USE_ASYNC = false // fuck you Akka, completely intrusive behaviour when using async

  final val prefix          = "FScape"
  def humanName : String    = prefix
  def tpe       : Obj.Type  = FScape

  type Repr[~ <: Sys[~]] = FScape[~]

  def isSingleton: Boolean = false  // hmmm, this depends?

  private lazy val _init: Unit = Runner.addFactory(this)

  def init(): Unit = _init

  def mkRunner[S <: synth.Sys[S]](obj: FScape[S])(implicit tx: S#Tx, universe: Runner.Universe[S]): Runner[S] =
    new Impl(tx.newHandle(obj))

  private final class Impl[S <: synth.Sys[S]](val objH: stm.Source[S#Tx, FScape[S]])
                                             (implicit val universe: Runner.Universe[S])
    extends BasicRunnerImpl[S] /*with ObjViewBase[S, Unit]*/ {

    private[this] val renderRef = Ref(Option.empty[Rendering[S]])
    private[this] val obsRef    = Ref(Disposable.empty[S#Tx])
//    private[this] val dispatchedState = Ref[Runner.State](Runner.Stopped)

    def tpe: Obj.Type = FScape

    object progress extends Runner.Progress[S#Tx] with ObservableImpl[S, Double] with ActionListener {
      private[this] val ref = Ref(-1.0)

      def current(implicit tx: S#Tx): Double = ref()

      def current_=(value: Double)(implicit tx: S#Tx): Unit = {
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
        cursor.step { implicit tx =>
          current = guiValue
        }
      }
    }

    protected def disposeData()(implicit tx: S#Tx): Unit = {
      obsRef   .swap(Disposable.empty).dispose()
      renderRef.swap(None).foreach  (_.dispose())
    }

    // XXX TODO --- pass `attr` to `obj.run`
    def prepare(attr: Runner.Attr)(implicit tx: S#Tx): Unit =
      state = Runner.Prepared

    def run()(implicit tx: S#Tx): Unit = {
      val obj = objH()
      messages.current = Nil
      state = Runner.Running
      val cfg = Control.Config()
      cfg.progressReporter = { p =>
        progress.push(p.total)
      }
      cfg.useAsync = DEBUG_USE_ASYNC
      val r: Rendering[S] = obj.run(cfg)
      renderRef.swap(Some(r)).foreach(_.dispose())
      obsRef   .swap(Disposable.empty) .dispose()
      val newObs = r.reactNow { implicit tx => {
        case Rendering.Completed =>
          r.result match {
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
            case _ =>
          }
          state = Runner.Stopped  // XXX TODO: clear renderRef
        case _ =>
      }}
      obsRef() = newObs
    }

    def stop()(implicit tx: S#Tx): Unit = {
      renderRef.swap(None).foreach(_.dispose())
      state = Runner.Stopped
    }
  }
}
