/*
 *  In2Impl.scala
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

import akka.stream.{FanInShape2, Inlet, Outlet}
import akka.stream.stage.GraphStageLogic

/** Building block for `FanInShape2` type graph stage logic,
  * with no information regarding "hot" inlets.
  */
trait In2Impl[In0 <: BufLike, In1 <: BufLike, Out <: BufLike]
  extends Out1LogicImpl[Out, FanInShape2[In0, In1, Out]] {
  _: GraphStageLogic with Node =>

  // ---- impl ----

  protected final var bufIn0 : In0 = _
  protected final var bufIn1 : In1 = _
  protected final var bufOut0: Out = _

  protected final val in0 : Inlet[In0]  = shape.in0
  protected final val in1 : Inlet[In1]  = shape.in1
  protected final val out0: Outlet[Out] = shape.out

  override protected def stopped(): Unit = {
    freeInputBuffers()
    freeOutputBuffers()
  }

  protected final def freeInputBuffers(): Unit = {
    if (bufIn0 != null) {
      bufIn0.release()
      bufIn0 = null.asInstanceOf[In0]
    }
    if (bufIn1 != null) {
      bufIn1.release()
      bufIn1 = null.asInstanceOf[In1]
    }
  }

  protected final def freeOutputBuffers(): Unit =
    if (bufOut0 != null) {
      bufOut0.release()
      bufOut0 = null.asInstanceOf[Out]
    }
}