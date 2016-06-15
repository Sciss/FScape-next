/*
 *  GenIn1Impl.scala
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

package de.sciss.fscape.stream.impl

import akka.stream.FlowShape
import akka.stream.stage.GraphStageLogic
import de.sciss.fscape.stream.BufLike

/** Building block for generators with `FanInShape2` type graph stage logic.
  * A generator keeps producing output until down-stream is closed, and does
  * not care about upstream inlets being closed.
  */
trait GenIn1Impl[In >: Null <: BufLike, Out >: Null <: BufLike]
  extends InOutImpl[FlowShape[In, Out]] {
  _: GraphStageLogic =>

  // ---- impl ----

  protected final var bufIn0 : In  = _
  protected final var bufOut0: Out = _

  private[this] final var _canRead = false
  private[this] final var _inValid = false

  final def canRead: Boolean = _canRead
  final def inValid: Boolean = _inValid

  override def preStart(): Unit =
    pull(shape.in)

  override def postStop(): Unit = {
    freeInputBuffers()
    freeOutputBuffers()
  }

  protected final def readIns(): Int = {
    freeInputBuffers()
    val sh = shape
    if (isAvailable(sh.in)) {
      bufIn0 = grab(sh.in)
      tryPull(sh.in)
    }

    _inValid = true
    updateCanRead()
    ctrl.bufSize
  }

  protected final def freeInputBuffers(): Unit =
    if (bufIn0 != null) {
      bufIn0.release()
      bufIn0 = null
    }

  protected final def freeOutputBuffers(): Unit =
    if (bufOut0 != null) {
      bufOut0.release()
      bufOut0 = null
    }

  final def updateCanRead(): Unit = {
    val sh = shape
    // XXX TODO -- actually we should require that we have
    // acquired at least one buffer of each inlet. that could
    // be checked in `onUpstreamFinish` which should probably
    // close the stage if not a single buffer had been read!
    _canRead = (isClosed(sh.in) && _inValid) || isAvailable(sh.in)
  }

  new AuxInHandlerImpl     (shape.in , this)
  new ProcessOutHandlerImpl(shape.out, this)
}