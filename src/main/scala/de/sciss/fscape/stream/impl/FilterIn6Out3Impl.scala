/*
 *  FilterIn6Out3Impl.scala
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

import akka.stream.stage.GraphStageLogic
import de.sciss.fscape.stream.BufLike

/** Building block for `FanInShape5` type graph stage logic. */
trait FilterIn6Out3Impl[In0 >: Null <: BufLike, In1 >: Null <: BufLike, In2 >: Null <: BufLike,
In3 >: Null <: BufLike, In4 >: Null <: BufLike, In5 >: Null <: BufLike, Out0 >: Null <: BufLike, 
Out1 >: Null <: BufLike, Out2 >: Null <: BufLike]
  extends InOutImpl[In6Out3Shape[In0, In1, In2, In3, In4, In5, Out0, Out1, Out2]] {
  _: GraphStageLogic =>

  // ---- impl ----

  protected final var bufIn0 : In0  = _
  protected final var bufIn1 : In1  = _
  protected final var bufIn2 : In2  = _
  protected final var bufIn3 : In3  = _
  protected final var bufIn4 : In4  = _
  protected final var bufIn5 : In5  = _
  protected final var bufOut0: Out0 = _
  protected final var bufOut1: Out1 = _
  protected final var bufOut2: Out2 = _

  private[this] final var _canRead = false
  private[this] final var _inValid = false

  final def canRead: Boolean = _canRead
  final def inValid: Boolean = _inValid

  override def preStart(): Unit =
    shape.inlets.foreach(pull(_))

  override def postStop(): Unit = {
    freeInputBuffers()
    freeOutputBuffers()
  }

  protected final def readIns(): Int = {
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

    if (isAvailable(sh.in4)) {
      bufIn4 = grab(sh.in4)
      tryPull(sh.in4)
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
    if (bufIn2 != null) {
      bufIn2.release()
      bufIn2 = null
    }
    if (bufIn3 != null) {
      bufIn3.release()
      bufIn3 = null
    }
    if (bufIn4 != null) {
      bufIn4.release()
      bufIn4 = null
    }
    if (bufIn5 != null) {
      bufIn5.release()
      bufIn5 = null
    }
  }

  protected final def freeOutputBuffers(): Unit = {
    if (bufOut0 != null) {
      bufOut0.release()
      bufOut0 = null
    }
    if (bufOut1 != null) {
      bufOut1.release()
      bufOut1 = null
    }
    if (bufOut2 != null) {
      bufOut2.release()
      bufOut2 = null
    }
  }

  final def updateCanRead(): Unit = {
    val sh = shape
    _canRead = isAvailable(sh.in0) &&
      ((isClosed(sh.in1) && _inValid) || isAvailable(sh.in1)) &&
      ((isClosed(sh.in2) && _inValid) || isAvailable(sh.in2)) &&
      ((isClosed(sh.in3) && _inValid) || isAvailable(sh.in3)) &&
      ((isClosed(sh.in4) && _inValid) || isAvailable(sh.in4))
  }

  new ProcessInHandlerImpl (shape.in0, this)
  new AuxInHandlerImpl     (shape.in1, this)
  new AuxInHandlerImpl     (shape.in2, this)
  new AuxInHandlerImpl     (shape.in3, this)
  new AuxInHandlerImpl     (shape.in4, this)
  new AuxInHandlerImpl     (shape.in5, this)
  ??? // new ProcessOutHandlerImpl(shape.out, this)
}