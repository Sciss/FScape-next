/*
 *  Sink2Impl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream
package impl

import akka.stream.Shape
import akka.stream.stage.GraphStageLogic

trait SinkImpl[S <: Shape]
  extends InOutImpl[S] {
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

/** Building block for sinks with `Sink2Shape` type graph stage logic.
  * A sink keeps consuming input until left inlet is closed.
  */
trait Sink2Impl[In0 >: Null <: BufLike, In1 >: Null <: BufLike]
  extends SinkImpl[SinkShape2[In0, In1]] {
  _: GraphStageLogic =>

  // ---- impl ----

  protected final var bufIn0: In0 = _
  protected final var bufIn1: In1 = _

  private[this] final var _canRead = false
  private[this] final var _inValid = false

  final def canRead: Boolean = _canRead
  final def inValid: Boolean = _inValid

  override def preStart(): Unit = {
    val sh = shape
    pull(sh.in0)
    pull(sh.in1)
  }

  override def postStop(): Unit = {
    freeInputBuffers()
    freeOutputBuffers()
  }

  protected final def readIns(): Int = {
    freeInputBuffers()
    val sh = shape

    bufIn0 = grab(sh.in0)
    bufIn0.assertAllocated()
    tryPull(sh.in0)

    if (isAvailable(sh.in1)) {
      bufIn1 = grab(sh.in1)
      tryPull(sh.in1)
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
    val sh = shape
    // XXX TODO -- actually we should require that we have
    // acquired at least one buffer of each inlet. that could
    // be checked in `onUpstreamFinish` which should probably
    // close the stage if not a single buffer had been read!
    _canRead = isAvailable(sh.in0) &&
      ((isClosed(sh.in1) && _inValid) || isAvailable(sh.in1))
  }

  new ProcessInHandlerImpl(shape.in0, this)
  new AuxInHandlerImpl    (shape.in1, this)
}