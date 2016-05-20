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
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import de.sciss.fscape.stream.BufLike

/** Building block for `FanInShape2` type graph stage logic. */
trait FilterIn1Impl[In >: Null <: BufLike, Out >: Null <: BufLike]
  extends FilterInImpl[FlowShape[In, Out]] {
  _: GraphStageLogic =>

  // ---- impl ----

  protected final var bufIn : In  = _
  protected final var bufOut: Out = _

  private[this] final var _canRead = false

  protected final def canRead: Boolean = _canRead

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
    tryPull(sh.in)
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

  private[this] def updateCanRead(): Unit = {
    _canRead = isAvailable(shape.in)
    if (_canRead) process()
  }

  setHandler(shape.in, new InHandler {
    def onPush(): Unit = updateCanRead()

    override def onUpstreamFinish(): Unit = process() // may lead to `flushOut`
  })

  setHandler(shape.out, new OutHandler {
    def onPull(): Unit = process()
  })
}