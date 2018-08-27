/*
 *  SinkImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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

import akka.stream.{Inlet, Shape, SinkShape}
import akka.stream.stage.GraphStageLogic

trait SinkImpl[S <: Shape]
  extends FullInOutImpl[S] {
  _: GraphStageLogic =>

  /** Dummy, always returns `true`. */
  final def canWrite: Boolean = true

  /** Dummy, no-op. */
  final def updateCanWrite(): Unit = ()

  /** Dummy, no-op. */
  protected final def writeOuts(outOff: Int): Unit = ()

  /** Dummy, no-op. */
  protected final def freeOutputBuffers(): Unit = ()
}

/** Building block for sinks with `SinkShape` type graph stage logic.
  * A sink keeps consuming input until left inlet is closed.
  */
trait Sink1Impl[In0 >: Null <: BufLike]
  extends SinkImpl[SinkShape[In0]] {
  _: GraphStageLogic with Node =>

  // ---- impl ----

  protected final var bufIn0: In0 = _

  protected final val in0: Inlet[In0] = shape.in

  private[this] final var _canRead = false
  private[this] final var _inValid = false

  final def canRead: Boolean = _canRead
  final def inValid: Boolean = _inValid

  override def preStart(): Unit =
    pull(in0)

  override protected def stopped(): Unit = {
    freeInputBuffers()
    freeOutputBuffers()
  }

  protected final def readIns(): Int = {
    freeInputBuffers()

    bufIn0 = grab(in0)
    bufIn0.assertAllocated()
    tryPull(in0)

    _inValid = true
    _canRead = false
    bufIn0.size
  }

  protected final def freeInputBuffers(): Unit =
    if (bufIn0 != null) {
      bufIn0.release()
      bufIn0 = null
    }

  final def updateCanRead(): Unit =
    _canRead = isAvailable(in0)

  new ProcessInHandlerImpl(in0, this)
}

/** Building block for sinks with `Sink2Shape` type graph stage logic.
  * A sink keeps consuming input until left inlet is closed.
  */
trait Sink2Impl[In0 >: Null <: BufLike, In1 >: Null <: BufLike]
  extends SinkImpl[SinkShape2[In0, In1]] {
  _: GraphStageLogic with Node =>

  // ---- impl ----

  protected final var bufIn0: In0 = _
  protected final var bufIn1: In1 = _

  protected final val in0: Inlet[In0]  = shape.in0
  protected final val in1: Inlet[In1]  = shape.in1

  private[this] final var _canRead = false
  private[this] final var _inValid = false

  final def canRead: Boolean = _canRead
  final def inValid: Boolean = _inValid

  override def preStart(): Unit = {
    pull(in0)
    pull(in1)
  }

  override protected def stopped(): Unit = {
    freeInputBuffers()
    freeOutputBuffers()
  }

  protected final def readIns(): Int = {
    freeInputBuffers()

    bufIn0 = grab(in0)
    bufIn0.assertAllocated()
    tryPull(in0)

    if (isAvailable(in1)) {
      bufIn1 = grab(in1)
      tryPull(in1)
    }

    _inValid = true
    _canRead = false
    bufIn0.size
  }

  protected final def freeInputBuffers(): Unit = {
    if (bufIn0 != null) {
      bufIn0.release()
      bufIn0 = null
    }
    if (bufIn1 != null) {
      bufIn1.release()
      bufIn1 = null
    }
  }

  final def updateCanRead(): Unit = {
    // XXX TODO -- actually we should require that we have
    // acquired at least one buffer of each inlet. that could
    // be checked in `onUpstreamFinish` which should probably
    // close the stage if not a single buffer had been read!
    _canRead = isAvailable(in0) &&
      ((isClosed(in1) && _inValid) || isAvailable(in1))
  }

  new ProcessInHandlerImpl(in0, this)
  new AuxInHandlerImpl    (in1, this)
}