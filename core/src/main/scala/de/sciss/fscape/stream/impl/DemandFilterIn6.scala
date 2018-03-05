/*
 *  DemandFilterIn6.scala
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
import akka.stream.{FanInShape6, Inlet, Outlet}

/** Building block for `FanInShape6` type graph stage logic. */
trait DemandFilterIn6[In0 >: Null <: BufLike, In1 >: Null <: BufLike, In2 >: Null <: BufLike,
In3 >: Null <: BufLike, In4 >: Null <: BufLike, In5 >: Null <: BufLike, Out >: Null <: BufLike]
  extends Out1LogicImpl[Out, FanInShape6[In0, In1, In2, In3, In4, In5, Out]]
    with DemandInOutImpl[FanInShape6[In0, In1, In2, In3, In4, In5, Out]] {
  _: GraphStageLogic with Node =>

  // ---- impl ----

  protected final var bufIn0 : In0 = _
  protected final var bufIn1 : In1 = _
  protected final var bufIn2 : In2 = _
  protected final var bufIn3 : In3 = _
  protected final var bufIn4 : In4 = _
  protected final var bufIn5 : In5 = _
  protected final var bufOut0: Out = _

  protected final def in0: Inlet[In0] = shape.in0
  protected final def in1: Inlet[In1] = shape.in1
  protected final def in2: Inlet[In2] = shape.in2
  protected final def in3: Inlet[In3] = shape.in3
  protected final def in4: Inlet[In4] = shape.in4
  protected final def in5: Inlet[In5] = shape.in5

  private[this] final var _mainCanRead  = false
  private[this] final var _auxCanRead   = false
  private[this] final var _mainInValid  = false
  private[this] final var _auxInValid   = false
  private[this] final var _inValid      = false

  protected final def out0: Outlet[Out] = shape.out

  final def mainCanRead : Boolean = _mainCanRead
  final def auxCanRead  : Boolean = _auxCanRead
  final def mainInValid : Boolean = _mainInValid
  final def auxInValid  : Boolean = _auxInValid
  final def inValid     : Boolean = _inValid

  override def preStart(): Unit = {
    val sh = shape
    pull(sh.in0)
    pull(sh.in1)
    pull(sh.in2)
    pull(sh.in3)
    pull(sh.in4)
    pull(sh.in5)
  }

  override protected def stopped(): Unit = {
    freeInputBuffers()
    freeOutputBuffers()
  }

  protected final def readMainIns(): Int = {
    freeMainInBuffers()
    val sh        = shape
    bufIn0        = grab(sh.in0)
    bufIn0.assertAllocated()
    tryPull(sh.in0)

    if (!_mainInValid) {
      _mainInValid= true
      _inValid    = _auxInValid
    }

    _mainCanRead = false
    bufIn0.size
  }

  protected final def readAuxIns(): Int = {
    freeAuxInBuffers()
    val sh    = shape
    var sz    = 0

    if (isAvailable(sh.in1)) {
      bufIn1  = grab(sh.in1)
      sz      = bufIn1.size
      tryPull(sh.in1)
    }
    if (isAvailable(sh.in2)) {
      bufIn2  = grab(sh.in2)
      sz      = math.max(sz, bufIn2.size)
      tryPull(sh.in2)
    }
    if (isAvailable(sh.in3)) {
      bufIn3  = grab(sh.in3)
      sz      = math.max(sz, bufIn3.size)
      tryPull(sh.in3)
    }
    if (isAvailable(sh.in4)) {
      bufIn4  = grab(sh.in4)
      sz      = math.max(sz, bufIn4.size)
      tryPull(sh.in4)
    }
    if (isAvailable(sh.in5)) {
      bufIn5  = grab(sh.in5)
      sz      = math.max(sz, bufIn5.size)
      tryPull(sh.in5)
    }

    if (!_auxInValid) {
      _auxInValid = true
      _inValid    = _mainInValid
    }

    _auxCanRead = false
    sz
  }

  protected final def freeInputBuffers(): Unit = {
    freeMainInBuffers()
    freeAuxInBuffers()
  }

  private final def freeMainInBuffers(): Unit =
    if (bufIn0 != null) {
      bufIn0.release()
      bufIn0 = null
    }

  private final def freeAuxInBuffers(): Unit = {
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

  protected final def freeOutputBuffers(): Unit =
    if (bufOut0 != null) {
      bufOut0.release()
      bufOut0 = null
    }

  final def updateMainCanRead(): Unit =
    _mainCanRead = isAvailable(in0)

  final def updateAuxCanRead(): Unit = {
    val sh = shape
    _auxCanRead =
      ((isClosed(sh.in1) && _auxInValid) || isAvailable(sh.in1)) &&
      ((isClosed(sh.in2) && _auxInValid) || isAvailable(sh.in2)) &&
      ((isClosed(sh.in3) && _auxInValid) || isAvailable(sh.in3)) &&
      ((isClosed(sh.in4) && _auxInValid) || isAvailable(sh.in4)) &&
      ((isClosed(sh.in5) && _auxInValid) || isAvailable(sh.in5))
  }

  new DemandProcessInHandler(shape.in0, this)
  new DemandAuxInHandler    (shape.in1, this)
  new DemandAuxInHandler    (shape.in2, this)
  new DemandAuxInHandler    (shape.in3, this)
  new DemandAuxInHandler    (shape.in4, this)
  new DemandAuxInHandler    (shape.in5, this)
  new ProcessOutHandlerImpl (shape.out, this)
}

trait DemandFilterIn6D[In0 >: Null <: BufLike, In1 >: Null <: BufLike, In2 >: Null <: BufLike,
  In3 >: Null <: BufLike, In4 >: Null <: BufLike, In5 >: Null <: BufLike]
  extends DemandFilterIn6[In0, In1, In2, In3, In4, In5, BufD]
    with Out1DoubleImpl[FanInShape6[In0, In1, In2, In3, In4, In5, BufD]] {

  _: GraphStageLogic with Node =>
}