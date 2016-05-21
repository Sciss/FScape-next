/*
 *  GenIn2Impl.scala
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

import akka.stream.FanInShape2
import akka.stream.stage.GraphStageLogic
import de.sciss.fscape.stream.BufLike

/** Building block for generators with `FanInShape2` type graph stage logic.
  * A generator keeps producing output until down-stream is closed, and does
  * not care about upstream inlets being closed.
  */
trait GenIn2Impl[In0 >: Null <: BufLike, In1 >: Null <: BufLike, Out >: Null <: BufLike]
  extends InOutImpl[FanInShape2[In0, In1, Out]] {
  _: GraphStageLogic =>

  // ---- impl ----

  protected final var bufIn0: In0 = _
  protected final var bufIn1: In1 = _
  protected final var bufOut: Out = _

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

  protected final def readIns(): Unit = {
    freeInputBuffers()
    val sh = shape
    if (isAvailable(sh.in0)) {
      bufIn0 = grab(sh.in0)
      tryPull(sh.in0)
    }

    if (isAvailable(sh.in1)) {
      bufIn1 = grab(sh.in1)
      tryPull(sh.in1)
    }

    _inValid = true
    _canRead = false
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

  protected final def freeOutputBuffers(): Unit =
    if (bufOut != null) {
      bufOut.release()
      bufOut = null
    }

  final def updateCanRead(): Unit = {
    val sh = shape
    // XXX TODO -- actually we should require that we have
    // acquired at least one buffer of each inlet. that could
    // be checked in `onUpstreamFinish` which should probably
    // close the stage if not a single buffer had been read!
    _canRead = ((isClosed(sh.in0) && _inValid) || isAvailable(sh.in0)) &&
               ((isClosed(sh.in1) && _inValid) || isAvailable(sh.in1))
  }

  new AuxInHandlerImpl     (shape.in0, this)
  new AuxInHandlerImpl     (shape.in1, this)
  new ProcessOutHandlerImpl(shape.out, this)
}