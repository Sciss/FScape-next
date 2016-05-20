/*
 *  FilterIn4Impl.scala
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

import akka.stream.FanInShape4
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import de.sciss.fscape.stream.BufLike

/** Building block for `FanInShape3` type graph stage logic. */
trait FilterIn4Impl[In0 >: Null <: BufLike, In1 >: Null <: BufLike, In2 >: Null <: BufLike, In3 >: Null <: BufLike, Out >: Null <: BufLike]
  extends FilterInImpl[FanInShape4[In0, In1, In2, In3, Out]] {
  _: GraphStageLogic =>

  // ---- impl ----

  protected final var bufIn0: In0 = _
  protected final var bufIn1: In1 = _
  protected final var bufIn2: In2 = _
  protected final var bufIn3: In3 = _
  protected final var bufOut: Out = _

  private[this] final var _canRead = false

  protected final def canRead: Boolean = _canRead

  override def preStart(): Unit = {
    val sh = shape
    pull(sh.in0)
    pull(sh.in1)
    pull(sh.in2)
    pull(sh.in3)
  }

  override def postStop(): Unit = {
    freeInputBuffers()
    freeOutputBuffers()
  }

  protected final def readIns(): Unit = {
    freeInputBuffers()
    val sh    = shape
    bufIn0    = grab(sh.in0)
    bufIn0.assertAllocated()
    tryPull(sh.in0)

    if (isAvailable(sh.in1)) {
      bufIn1 = grab(sh.in1)
      tryPull(sh.in1)
    }

    if (isAvailable(sh.in2)) {
      bufIn2 = grab(sh.in2)
      tryPull(sh.in2)
    }

    if (isAvailable(sh.in3)) {
      bufIn3 = grab(sh.in3)
      tryPull(sh.in3)
    }

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
    if (bufIn2 != null) {
      bufIn2.release()
      bufIn2 = null
    }
    if (bufIn3 != null) {
      bufIn3.release()
      bufIn3 = null
    }
  }

  protected final def freeOutputBuffers(): Unit =
    if (bufOut != null) {
      bufOut.release()
      bufOut = null
    }

  private[this] def updateCanRead(): Unit = {
    val sh = shape
    _canRead = isAvailable(sh.in0) &&
      (isClosed(sh.in1) || isAvailable(sh.in1)) &&
      (isClosed(sh.in2) || isAvailable(sh.in2)) &&
      (isClosed(sh.in3) || isAvailable(sh.in3))
    if (_canRead) process()
  }

  setHandler(shape.in0, new InHandler {
    def onPush(): Unit = updateCanRead()

    override def onUpstreamFinish(): Unit = process() // may lead to `flushOut`
  })

  private[this] final val inIH = new InHandler {
    def onPush(): Unit = updateCanRead()

    override def onUpstreamFinish(): Unit = ()  // keep running
  }

  setHandler(shape.in1, inIH)
  setHandler(shape.in2, inIH)
  setHandler(shape.in3, inIH)

  setHandler(shape.out, new OutHandler {
    def onPull(): Unit = process()
  })
}