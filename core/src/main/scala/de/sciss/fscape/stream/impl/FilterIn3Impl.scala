/*
 *  FilterIn3Impl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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

import akka.stream.stage.GraphStageLogic
import akka.stream.{FanInShape3, Inlet, Outlet}

/** Building block for `FanInShape3` type graph stage logic. */
trait FilterIn3Impl[In0 >: Null <: BufLike, In1 >: Null <: BufLike, In2 >: Null <: BufLike, Out >: Null <: BufLike]
  extends Out1LogicImpl[Out, FanInShape3[In0, In1, In2, Out]] with FullInOutImpl[FanInShape3[In0, In1, In2, Out]] {
  _: GraphStageLogic with Node =>

  // ---- impl ----

  protected final var bufIn0 : In0 = _
  protected final var bufIn1 : In1 = _
  protected final var bufIn2 : In2 = _
  protected final var bufOut0: Out = _

  protected final def in0: Inlet[In0] = shape.in0
  protected final def in1: Inlet[In1] = shape.in1
  protected final def in2: Inlet[In2] = shape.in2

  private[this] final var _canRead = false
  private[this] final var _inValid = false

  protected final def out0: Outlet[Out] = shape.out

  final def canRead: Boolean = _canRead
  final def inValid: Boolean = _inValid

  override def preStart(): Unit = {
    val sh = shape
    pull(sh.in0)
    pull(sh.in1)
    pull(sh.in2)
  }

  override protected def stopped(): Unit = {
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
  }

  protected final def freeOutputBuffers(): Unit =
    if (bufOut0 != null) {
      bufOut0.release()
      bufOut0 = null
    }

  final def updateCanRead(): Unit = {
    val sh = shape
    _canRead = isAvailable(sh.in0) &&
      ((isClosed(sh.in1) && _inValid) || isAvailable(sh.in1)) &&
      ((isClosed(sh.in2) && _inValid) || isAvailable(sh.in2))
  }

  new ProcessInHandlerImpl (shape.in0, this)
  new AuxInHandlerImpl     (shape.in1, this)
  new AuxInHandlerImpl     (shape.in2, this)
  new ProcessOutHandlerImpl(shape.out, this)
}

trait FilterIn3DImpl[In0 >: Null <: BufLike, In1 >: Null <: BufLike, In2 >: Null <: BufLike]
  extends FilterIn3Impl[In0, In1, In2, BufD] with Out1DoubleImpl[FanInShape3[In0, In1, In2, BufD]] {

  _: GraphStageLogic with Node =>
}
