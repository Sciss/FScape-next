/*
 *  StageLogicImpl.scala
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

package de.sciss.fscape
package stream
package impl

import akka.stream.stage.{GraphStageLogic, InHandler}
import akka.stream.{Inlet, Shape}
import de.sciss.fscape.Log.{stream => logStream}

import scala.concurrent.{ExecutionContext, Future}

abstract class NodeImpl[+S <: Shape](protected final val name: String, val layer: Layer,
                                     final override val shape: S)
                                    (implicit final protected val control: Control)
  extends GraphStageLogic(shape) with Node {

  override def toString = s"$name-L@${hashCode.toHexString}"

  // very important: `getAsyncCallback` must only be called on the
  // graph-stage-logic constructor or `onPull`, `onPush`!
  private[this] val asyncF = getAsyncCallback { (f: () => Unit) =>
    f()
  }

  final def async(body: => Unit): Unit = asyncF.invoke(() => body)

  final def launchAsync(): Future[Unit] = {
    implicit val ex: ExecutionContext = control.config.executionContext
    asyncF.invokeWithFeedback(() => launch()).map(_ => ())
  }

  protected def launch(): Unit = {
    logStream.debug(s"$this - launch")
//    if (NodeImpl.BLA) {
//      NodeImpl.BLA = false
//      if (toString.contains("ResizeWindow")) {
//        println("Aqui")
//      }
//    }
    // N.B.: `tryPull` not `pull`, because now the graph interpreter may have processed some nodes already
    shape.inlets.foreach { in =>
      if (!isClosed(in) && !hasBeenPulled(in)) {
        pull(in)
      }
    }
  }

  final def failAsync(ex: Exception): Unit =
    asyncF.invoke { () =>
      failStage(ex)
    }

  protected final def notifyFail(ex: Throwable): Unit = {
    control.nodeFailed(this, ex)
    failStage(ex)
  }

  def completeAsync(): Future[Unit] = {
    implicit val ex: ExecutionContext = control.config.executionContext
    asyncF.invokeWithFeedback { () =>
      logStream.debug(s"$this - completeAsync")
      completeStage()
    }.map(_ => ())
  }

  // XXX TODO --- should use Handlers now?
  @deprecated("Should move to using Handlers", since = "2.35.1")
  abstract class InHandlerImpl[A, E <: BufElem[A]](in: Inlet[E])
    extends InHandler {

    private[this] var hasValue      = false
    private[this] var everHadValue  = false

    private[this] var _buf    : E   = _
    private[this] var _offset : Int = 0
    final var mostRecent      : A   = _

    // ---- abstract ----

    protected def notifyValue(): Unit

    // ---- impl ---

    final def offset: Int = _offset

    final def bufRemain: Int = if (_buf == null) 0 else _buf.size - _offset

    final def buf: E = _buf

    final def available(max: Int): Int =
      if (isClosed(in) && !isAvailable(in)) {
        // if the stream has terminated, we will repeat last value forever
        if (_buf != null || everHadValue) max else 0
      } else if (_buf != null) {
        // otherwise, if there is buffer, we can read that
        math.min(max, _buf.size - _offset)
      } else {
        // otherwise, we can't output anything
        0
      }

    override final def toString: String = in.toString //  s"$logic.$in"

    final def updateOffset(n: Int): Unit =
      if (_buf != null) {
        _offset = n
        assert (_offset <= _buf.size)
        if (bufRemain == 0) freeBuffer()
      }

    final def hasNext: Boolean =
      (_buf != null) || !isClosed(in) || isAvailable(in)

    final def freeBuffer(): Unit =
      if (_buf != null) {
        mostRecent = _buf.buf(_buf.size - 1)
        _buf.release()
        _buf = null.asInstanceOf[E]
      }

    final def next(): Unit = {
      hasValue = false
      if (bufRemain > 0) {
        ackValue()
      } else {
        freeBuffer()
        if (isAvailable(in)) onPush()
      }
    }

    final def clearHasValue(): Unit =
      hasValue = false

    final def takeValue(): A =
      if (_buf == null) {
        mostRecent
      } else {
        val i = _buf.buf(_offset)
        _offset += 1
        if (_offset == _buf.size) {
          freeBuffer()
        }
        i
      }

    final def peekValue(): A =
      if (_buf == null) {
        mostRecent
      } else {
        _buf.buf(_offset)
      }

    final def skipValue(): Unit =
      if (_buf != null) {
        _offset += 1
        if (_offset == _buf.size) {
          freeBuffer()
        }
      }

    final def onPush(): Unit = {
      val cond = !hasValue && _buf == null
      logStream.debug(s"onPush() $this - !hasValue = $cond")
      if (cond) {
//        assert(_buf == null)
        _buf = grab(in)
        assert(_buf.size > 0)
        _offset = 0
        ackValue()
        tryPull(in)
      }
    }

    private def ackValue(): Unit = {
      hasValue      = true
      everHadValue  = true
      notifyValue()
    }

    final override def onUpstreamFinish(): Unit = {
      logStream.info(s"onUpstreamFinish() $this - hasValue = $hasValue, everHadValue = $everHadValue")
      if (!isAvailable(in)) {
        if (everHadValue) {
          if (!hasValue) ackValue()
        } else {
          super.onUpstreamFinish()
        }
      }
    }

    setHandler(in, this)
  }
}

trait NodeHasInitImpl extends NodeHasInit {
  _: GraphStageLogic =>

  private[this] var _init = false

  protected def init(): Unit = ()

  protected final def isInitialized: Boolean = _init

  final def initAsync(): Future[Unit] = {
    val async = getAsyncCallback { _: Unit =>
      logStream.debug(s"$this - initAsync")
      init()
      _init = true
    }

    implicit val ex: ExecutionContext = control.config.executionContext
    async.invokeWithFeedback(()).map(_ => ())
  }
}