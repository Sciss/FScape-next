/*
 *  DemandFilterIn3.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.stream.impl.deprecated

import akka.stream.stage.GraphStageLogic
import akka.stream.{FanInShape3, Inlet, Outlet}
import de.sciss.fscape.stream.{BufD, BufLike, Node}

/** Building block for `FanInShape3` type graph stage logic. */
@deprecated("Should move to using Handlers", since = "2.35.1")
trait DemandFilterIn3[In0 >: Null <: BufLike, In1 >: Null <: BufLike, In2 >: Null <: BufLike, Out >: Null <: BufLike]
  extends Out1LogicImpl[Out, FanInShape3[In0, In1, In2, Out]]
    with DemandInOutImpl[FanInShape3[In0, In1, In2, Out]] {
  _: GraphStageLogic with Node =>

  // ---- impl ----

  protected final var bufIn0 : In0 = _
  protected final var bufIn1 : In1 = _
  protected final var bufIn2 : In2 = _
  protected final var bufOut0: Out = _

  protected final def in0: Inlet[In0] = shape.in0
  protected final def in1: Inlet[In1] = shape.in1
  protected final def in2: Inlet[In2] = shape.in2

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
      ((isClosed(sh.in2) && _auxInValid) || isAvailable(sh.in2))
  }

  new DemandProcessInHandler(shape.in0, this)
  new DemandAuxInHandler    (shape.in1, this)
  new DemandAuxInHandler    (shape.in2, this)
  new ProcessOutHandlerImpl (shape.out, this)
}

@deprecated("Should move to using Handlers", since = "2.35.1")
trait DemandFilterIn3D[In0 >: Null <: BufLike, In1 >: Null <: BufLike, In2 >: Null <: BufLike]
  extends DemandFilterIn3[In0, In1, In2, BufD] with Out1DoubleImpl[FanInShape3[In0, In1, In2, BufD]] {

  _: GraphStageLogic with Node =>
}