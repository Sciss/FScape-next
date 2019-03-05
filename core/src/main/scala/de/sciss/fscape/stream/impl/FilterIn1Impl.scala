/*
 *  FilterIn1Impl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
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

import akka.stream.{FlowShape, Inlet, Outlet}
import akka.stream.stage.GraphStageLogic

/** Building block for `FanInShape2` type graph stage logic. */
trait FilterIn1Impl[In >: Null <: BufLike, Out >: Null <: BufLike]
  extends Out1LogicImpl[Out, FlowShape[In, Out]] with FullInOutImpl[FlowShape[In, Out]] {
  _: GraphStageLogic with Node =>

  // ---- impl ----

  protected final var bufIn0 : In  = _
  protected final var bufOut0: Out = _

  protected final val in0  : Inlet [In]  = shape.in
  protected final val out0 : Outlet[Out] = shape.out

  private[this] final var _canRead = false
  private[this] final var _inValid = false

  final def canRead: Boolean = _canRead
  final def inValid: Boolean = _inValid

  override def preStart(): Unit =
    pull(in0)

  override protected def stopped(): Unit = {
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
    if (bufOut0 != null) {
      bufOut0.release()
      bufOut0 = null
    }

  final def updateCanRead(): Unit =
    _canRead = isAvailable(in0)

  new ProcessInHandlerImpl (in0 , this)
  new ProcessOutHandlerImpl(out0, this)
}

trait FilterIn1IImpl[In >: Null <: BufLike] extends FilterIn1Impl[In, BufI] with Out1IntImpl[FlowShape[In, BufI]] {

  _: GraphStageLogic with Node =>
}

trait FilterIn1DImpl[In >: Null <: BufLike] extends FilterIn1Impl[In, BufD] with Out1DoubleImpl[FlowShape[In, BufD]] {

  _: GraphStageLogic with Node =>
}

trait FilterIn1LImpl[In >: Null <: BufLike] extends FilterIn1Impl[In, BufL] with Out1LongImpl[FlowShape[In, BufL]] {

  _: GraphStageLogic with Node =>
}