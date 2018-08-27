/*
 *  GenIn2Impl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream
package impl

import akka.stream.{FanInShape2, Inlet, Outlet}
import akka.stream.stage.GraphStageLogic

/** Building block for generators with `FanInShape2` type graph stage logic.
  * A generator keeps producing output until down-stream is closed, and does
  * not care about upstream inlets being closed.
  */
trait GenIn2Impl[In0 >: Null <: BufLike, In1 >: Null <: BufLike, Out >: Null <: BufLike]
  extends Out1LogicImpl[Out, FanInShape2[In0, In1, Out]] with FullInOutImpl[FanInShape2[In0, In1, Out]] {
  _: GraphStageLogic with Node =>

  // ---- impl ----

  protected final var bufIn0 : In0 = _
  protected final var bufIn1 : In1 = _
  protected final var bufOut0: Out = _

  protected final def in0 : Inlet [In0] = shape.in0
  protected final def in1 : Inlet [In1] = shape.in1
  protected final def out0: Outlet[Out] = shape.out

  private[this] final var _canRead = false
  private[this] final var _inValid = false

  final def canRead: Boolean = _canRead
  final def inValid: Boolean = _inValid

  override def preStart(): Unit = {
    val sh = shape
    pull(sh.in0)
    pull(sh.in1)
  }

  override protected def stopped(): Unit = {
    freeInputBuffers()
    freeOutputBuffers()
  }

  protected final def readIns(): Int = {
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
    updateCanRead()
    control.blockSize
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

trait GenIn2DImpl[In0 >: Null <: BufLike, In1 >: Null <: BufLike]
  extends GenIn2Impl[In0, In1, BufD]
    with Out1DoubleImpl[FanInShape2[In0, In1, BufD]] {
  _: GraphStageLogic with Node =>
}

trait GenIn2IImpl[In0 >: Null <: BufLike, In1 >: Null <: BufLike]
  extends GenIn2Impl[In0, In1, BufI]
    with Out1IntImpl[FanInShape2[In0, In1, BufI]] {
  _: GraphStageLogic with Node =>
}