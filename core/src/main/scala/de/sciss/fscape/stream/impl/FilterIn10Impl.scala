/*
 *  FilterIn10Impl.scala
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

package de.sciss.fscape
package stream
package impl

import akka.stream.stage.GraphStageLogic
import akka.stream.{FanInShape10, Inlet, Outlet}

/** Building block for `FanInShape10` type graph stage logic.
  * XXX TODO -- should be macro- or template-generated
  */
trait FilterIn10Impl[
    In0 >: Null <: BufLike, In1 >: Null <: BufLike, In2 >: Null <: BufLike, In3 >: Null <: BufLike,
    In4 >: Null <: BufLike, In5 >: Null <: BufLike, In6 >: Null <: BufLike, In7 >: Null <: BufLike,
    In8 >: Null <: BufLike, In9 >: Null <: BufLike,
    Out >: Null <: BufLike]
  extends Out1LogicImpl[Out, FanInShape10[In0, In1, In2, In3, In4, In5, In6, In7, In8, In9, Out]]
    with FullInOutImpl[FanInShape10[In0, In1, In2, In3, In4, In5, In6, In7, In8, In9, Out]] {
  _: GraphStageLogic with Node =>

  // ---- impl ----

  protected final var bufIn0 : In0 = _
  protected final var bufIn1 : In1 = _
  protected final var bufIn2 : In2 = _
  protected final var bufIn3 : In3 = _
  protected final var bufIn4 : In4 = _
  protected final var bufIn5 : In5 = _
  protected final var bufIn6 : In6 = _
  protected final var bufIn7 : In7 = _
  protected final var bufIn8 : In8 = _
  protected final var bufIn9 : In9 = _
  protected final var bufOut0: Out = _

  protected final def in0: Inlet[In0] = shape.in0
  protected final def in1: Inlet[In1] = shape.in1
  protected final def in2: Inlet[In2] = shape.in2
  protected final def in3: Inlet[In3] = shape.in3
  protected final def in4: Inlet[In4] = shape.in4
  protected final def in5: Inlet[In5] = shape.in5
  protected final def in6: Inlet[In6] = shape.in6
  protected final def in7: Inlet[In7] = shape.in7
  protected final def in8: Inlet[In8] = shape.in8
  protected final def in9: Inlet[In9] = shape.in9

  protected final def out0: Outlet[Out] = shape.out

  private[this] final var _canRead = false
  private[this] final var _inValid = false

  final def canRead: Boolean = _canRead
  final def inValid: Boolean = _inValid

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
    if (isAvailable(sh.in3)) {
      bufIn3 = grab(sh.in3)
      tryPull(sh.in3)
    }
    if (isAvailable(sh.in4)) {
      bufIn4 = grab(sh.in4)
      tryPull(sh.in4)
    }
    if (isAvailable(sh.in5)) {
      bufIn5 = grab(sh.in5)
      tryPull(sh.in5)
    }
    if (isAvailable(sh.in6)) {
      bufIn6 = grab(sh.in6)
      tryPull(sh.in6)
    }
    if (isAvailable(sh.in7)) {
      bufIn7 = grab(sh.in7)
      tryPull(sh.in7)
    }
    if (isAvailable(sh.in8)) {
      bufIn8 = grab(sh.in8)
      tryPull(sh.in8)
    }
    if (isAvailable(sh.in9)) {
      bufIn9 = grab(sh.in9)
      tryPull(sh.in9)
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
    if (bufIn6 != null) {
      bufIn6.release()
      bufIn6 = null
    }
    if (bufIn7 != null) {
      bufIn7.release()
      bufIn7 = null
    }
    if (bufIn8 != null) {
      bufIn8.release()
      bufIn8 = null
    }
    if (bufIn9 != null) {
      bufIn9.release()
      bufIn9 = null
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
      ((isClosed(sh.in2) && _inValid) || isAvailable(sh.in2)) &&
      ((isClosed(sh.in3) && _inValid) || isAvailable(sh.in3)) &&
      ((isClosed(sh.in4) && _inValid) || isAvailable(sh.in4)) &&
      ((isClosed(sh.in5) && _inValid) || isAvailable(sh.in5)) &&
      ((isClosed(sh.in6) && _inValid) || isAvailable(sh.in6)) &&
      ((isClosed(sh.in7) && _inValid) || isAvailable(sh.in7)) &&
      ((isClosed(sh.in8) && _inValid) || isAvailable(sh.in8)) &&
      ((isClosed(sh.in3) && _inValid) || isAvailable(sh.in3))
  }

  new ProcessInHandlerImpl (shape.in0, this)
  for (i <- 1 to 9) new AuxInHandlerImpl(shape.inlets(i), this)
  new ProcessOutHandlerImpl(shape.out, this)
}

trait FilterIn10DImpl[In0 >: Null <: BufLike, In1 >: Null <: BufLike, In2 >: Null <: BufLike, In3 >: Null <: BufLike,
In4 >: Null <: BufLike, In5 >: Null <: BufLike, In6 >: Null <: BufLike, In7 >: Null <: BufLike,
In8 >: Null <: BufLike, In9 >: Null <: BufLike]
  extends FilterIn10Impl[In0, In1, In2, In3, In4, In5, In6, In7, In8, In9, BufD]
    with Out1DoubleImpl[FanInShape10[In0, In1, In2, In3, In4, In5, In6, In7, In8, In9, BufD]] {
  _: GraphStageLogic with Node =>
}

