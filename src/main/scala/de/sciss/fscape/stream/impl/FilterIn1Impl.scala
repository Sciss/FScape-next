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

package de.sciss.fscape.stream.impl

import akka.stream.FlowShape
import akka.stream.stage.GraphStageLogic
import de.sciss.fscape.stream.BufLike

/** Building block for `FanInShape2` type graph stage logic. */
trait FilterIn1Impl[In >: Null <: BufLike, Out >: Null <: BufLike]
  extends InOutImpl[FlowShape[In, Out]] {
  _: GraphStageLogic =>

  // ---- impl ----

  protected final var bufIn : In  = _
  protected final var bufOut: Out = _

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

  protected final def readIns(): Unit = {
    freeInputBuffers()
    val sh    = shape
    bufIn     = grab(sh.in)
    bufIn.assertAllocated()
    tryPull(sh.in)
    _inValid = true
    _canRead = false
  }

  protected final def freeInputBuffers(): Unit =
    if (bufIn != null) {
      bufIn.release()
      bufIn = null
    }

  protected final def freeOutputBuffers(): Unit =
    if (bufOut != null) {
      bufOut.release()
      bufOut = null
    }

  final def updateCanRead(): Unit =
    _canRead = isAvailable(shape.in)

  new ProcessInHandlerImpl (shape.in , this)
  new ProcessOutHandlerImpl(shape.out, this)
}