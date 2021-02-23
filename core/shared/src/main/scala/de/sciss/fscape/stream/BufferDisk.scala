/*
 *  BufferDisk.scala
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

package de.sciss.fscape
package stream

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import de.sciss.fscape.Log.{stream => logStream}
import de.sciss.fscape.stream.impl.{NodeHasInitImpl, NodeImpl, StageImpl}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// XXX TODO --- we could use a "quasi-circular"
// structure? this is, overwrite parts of the file
// that were already read
object BufferDisk {
  def apply[A, E <: BufElem[A]](in: Outlet[E])(implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "BufferDisk"

  private type Shp[E] = FlowShape[E, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FlowShape(
      in  = Inlet [E](s"$name.in" ),
      out = Outlet[E](s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends NodeImpl(name, layer, shape) with NodeHasInitImpl with InHandler with OutHandler {

    private[this] var futAF: Future[AsyncFileBuffer[A]]  = _
    private[this] var afReady         = false
    private[this] var af: AsyncFileBuffer[A]  = _
    private[this] val bufSize       = ctrl.blockSize

    private[this] var framesWritten = 0L
    private[this] var framesRead    = 0L

    private[this] var taskBusy        = false
    private[this] var taskPendingPush = false
    private[this] var taskPendingPull = false

    setHandlers(shape.in, shape.out, this)

    override protected def launch(): Unit = {
      super.launch()
      futAF = task("open") {
        AsyncFileBuffer()
      } { _af =>
        af      = _af
        afReady = true
      }
      ()
//      onPull()  // needed for asynchronous logic
    }

    // be sure that `map` on async file buffer futures are
    // always executed on the stream thread
    implicit val exec: ExecutionContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit = async(runnable.run())

      override def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
    }

    override protected def stopped(): Unit = {
      super.stopped()
      if (futAF != null) {
        futAF.foreach { _af =>
          _af.dispose()
          af      = null
          afReady = false
        }
      }
    }

    private def task[B](name: String)(body: => Future[B])(cont: B => Unit): Future[B] = {
      require (!taskBusy)
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
        taskBusy = true
        fut.onComplete {
          case Success(res) =>
            // println(s"$this - task($name) onSuccess")
            taskBusy = false
            cont(res)
            taskPending()
          case Failure(ex) =>
            logStream.debug(s"$this - task($name) onFailure")
            taskBusy = false
            failStage(ex)
        }
      }
      fut
    }

    private def taskPending(): Unit = {
      if (!taskBusy && taskPendingPush) onPush()
      if (!taskBusy && taskPendingPull) onPull()
    }

    def onPush(): Unit =
      if (taskBusy || !afReady) {
        taskPendingPush = true
      } else {
        taskPendingPush = false
        val bufIn = grab(shape.in)
        tryPull(shape.in)
        val chunk = bufIn.size
        logStream.debug(s"onPush(${shape.in}) $chunk; read = $framesRead; written = $framesWritten")

        if (af.position != framesWritten) af.position = framesWritten
        task("write") {
          af.write(bufIn.buf, 0, chunk)
        } { _ =>
          framesWritten += chunk
          bufIn.release()
          if (isAvailable(shape.out)) onPull()
        }
        ()
      }

    def onPull(): Unit =
      if (taskBusy || !afReady) {
        taskPendingPull = true
      } else {
        taskPendingPull = false
        pullImpl()
      }

    private def pullImpl(): Unit = {
      val inputDone   = isClosed(shape.in) && !isAvailable(shape.in)
      val framesAvail = framesWritten - framesRead
      if (!inputDone && framesAvail < bufSize) return

      val chunk = math.min(bufSize, framesAvail).toInt
      logStream.debug(s"onPull(${shape.out}) $chunk; read = $framesRead; written = $framesWritten")
      if (chunk == 0) {
        if (inputDone) {
          logStream.info(s"onPull() -> completeStage $this")
          completeStage()
        }

      } else {
        if (af.position != framesRead) af.position = framesRead
        val bufOut = tpe.allocBuf() // ctrl.borrowBufD()
        task("read") {
          af.read(bufOut.buf, 0, chunk)
        } { _ =>
          framesRead += chunk
          bufOut.size = chunk
          push(shape.out, bufOut)
        }
        ()
      }
    }

    // in closed
    override def onUpstreamFinish(): Unit = {
      logStream.info(s"onUpstreamFinish(${shape.in}); read = $framesRead; written = $framesWritten")
      if (isAvailable(shape.out)) onPull()
    }
  }
}