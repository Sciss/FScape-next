/*
 *  FilterIn1Impl.scala
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

import akka.stream.FlowShape
import akka.stream.stage.GraphStageLogic

/** Building block for `FanInShape2` type graph stage logic. */
trait FilterIn1Impl[In >: Null <: BufLike, Out >: Null <: BufLike]
  extends InOutImpl[FlowShape[In, Out]] {
  _: GraphStageLogic =>

  // ---- impl ----

  protected final var bufIn0: In  = _
  protected final var bufOut: Out = _

  protected final val in0 = shape.in
  protected final val out = shape.out

  private[this] final var _canRead = false
  private[this] final var _inValid = false

  final def canRead: Boolean = _canRead
  final def inValid: Boolean = _inValid

  override def preStart(): Unit =
    pull(in0)

  override def postStop(): Unit = {
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

  protected final def freeOutputBuffers(): Unit =
    if (bufOut != null) {
      bufOut.release()
      bufOut = null
    }

  final def updateCanRead(): Unit =
    _canRead = isAvailable(in0)

  new ProcessInHandlerImpl (in0, this)
  new ProcessOutHandlerImpl(out, this)
}