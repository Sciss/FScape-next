/*
 *  AsyncTaskLogic.scala
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

package de.sciss.fscape.stream.impl

import de.sciss.fscape.Log.{stream => logStream}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** A mixin for GraphStageLogic that allows to spawn
  * interior asynchronous tasks. The futures are
  * handled on a special execution context that
  * ensures code runs within Akka stream.
  */
trait AsyncTaskLogic {
  this: NodeImpl[_] =>

  private[this] var _taskBusy = false

  protected final def taskBusy: Boolean = _taskBusy

  protected def taskPending(): Unit

  implicit protected final val execAsync: ExecutionContext = new ExecutionContext {
    override def execute(runnable: Runnable): Unit = async(runnable.run())

    override def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
  }

  protected final def task[B](name: String)(body: => Future[B])(cont: B => Unit): Future[B] = {
    require (!_taskBusy)
    // println(s"$this - task($name)")
    val fut = body
    if (fut.isCompleted) {  // check immediately, do not penalise underlying synchronous API
      // println(s"$this - task($name) immediately")
      val tr = fut.value.get
      if (tr.isSuccess) cont(tr.get)
      else {
        val ex = tr.failed.get
        failStage(ex)
      }
    } else {
      _taskBusy = true
      fut.onComplete {
        case Success(res) =>
          // println(s"$this - task($name) onSuccess")
          _taskBusy = false
          cont(res)
          taskPending()
        case Failure(ex) =>
          logStream.debug(s"$this - task($name) onFailure")
          _taskBusy = false
          failStage(ex)
      }
    }
    fut
  }
}
