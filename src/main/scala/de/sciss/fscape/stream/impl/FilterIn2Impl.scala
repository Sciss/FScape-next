/*
 *  FilterIn2Impl.scala
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

import akka.stream.FanInShape2
import akka.stream.stage.GraphStageLogic

/** Building block for `FanInShape2` type graph stage logic. */
trait FilterIn2Impl[In0 >: Null <: BufLike, In1 >: Null <: BufLike, Out >: Null <: BufLike]
  extends Out1LogicImpl[Out, FanInShape2[In0, In1, Out]] {
  _: GraphStageLogic =>

  // ---- impl ----

  protected final var bufIn0 : In0 = _
  protected final var bufIn1 : In1 = _
  protected final var bufOut0: Out = _

  protected final val in0  = shape.in0
  protected final val in1  = shape.in1
  protected final val out0 = shape.out

  private[this] final var _canRead = false
  private[this] final var _inValid = false

  final def canRead: Boolean = _canRead
  final def inValid: Boolean = _inValid

  override def preStart(): Unit = {
    pull(in0)
    pull(in1)
  }

  override def postStop(): Unit = {
    freeInputBuffers()
    freeOutputBuffers()
  }

  protected /* final */ def readIns(): Int = {
    freeInputBuffers()
    bufIn0    = grab(in0)
    bufIn0.assertAllocated()
    tryPull(in0)

    if (isAvailable(in1)) {
      bufIn1 = grab(in1)
      tryPull(in1)
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

  protected final def freeOutputBuffers(): Unit =
    if (bufOut0 != null) {
      bufOut0.release()
      bufOut0 = null
    }

  final def updateCanRead(): Unit = {
    _canRead = isAvailable(in0) &&
      ((isClosed(in1) && _inValid) || isAvailable(in1))
  }

  new ProcessInHandlerImpl (in0 , this)
  new AuxInHandlerImpl     (in1 , this)
  new ProcessOutHandlerImpl(out0, this)
}

trait FilterIn2DImpl[In0 >: Null <: BufLike, In1 >: Null <: BufLike]
  extends FilterIn2Impl[In0, In1, BufD] with Out1DoubleImpl[FanInShape2[In0, In1, BufD]] {

  _: GraphStageLogic =>
}
