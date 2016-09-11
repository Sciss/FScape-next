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

/** Building block for `FanInShape2` type graph stage logic,
  * where left inlet is "hot" and terminates process.
  */
trait FilterIn2Impl[In0 >: Null <: BufLike, In1 >: Null <: BufLike, Out >: Null <: BufLike]
  extends In2Impl[In0, In1, Out] {
  _: GraphStageLogic with Node =>

  // ---- impl ----

  private[this] final var _canRead = false
  private[this] final var _inValid = false

  final def canRead: Boolean = _canRead
  final def inValid: Boolean = _inValid

  protected final def readIns(): Int = {
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

  final def updateCanRead(): Unit =
    _canRead = isAvailable(in0) &&
      ((isClosed(in1) && _inValid) || isAvailable(in1))

  new ProcessInHandlerImpl (in0 , this)
  new AuxInHandlerImpl     (in1 , this)
  new ProcessOutHandlerImpl(out0, this)
}

trait FilterIn2DImpl[In0 >: Null <: BufLike, In1 >: Null <: BufLike]
  extends FilterIn2Impl[In0, In1, BufD] with Out1DoubleImpl[FanInShape2[In0, In1, BufD]] {

  _: GraphStageLogic with Node =>
}