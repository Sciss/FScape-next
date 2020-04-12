/*
 *  SinkImpl.scala
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

package de.sciss.fscape.stream.impl.deprecated

import akka.stream.stage.GraphStageLogic
import akka.stream.{Inlet, Shape, SinkShape}
import de.sciss.fscape.stream.{BufLike, Node}

@deprecated("Should move to using Handlers", since = "2.35.1")
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
@deprecated("Should move to using Handlers", since = "2.35.1")
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
