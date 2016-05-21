/*
 *  WindowedFilterLogicImpl.scala
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
import akka.stream.{FanInShape, Inlet}
import de.sciss.fscape.stream.BufLike

trait WindowedFilterLogicImpl[In0 >: Null <: BufLike, Out >: Null <: BufLike, Shape <: FanInShape[Out]]
  extends WindowedLogicImpl[In0, Out, Shape] {

  _: GraphStageLogic =>

  // ---- abstract ----

  protected def in0: Inlet[In0]

  protected var bufIn0: In0

  // ---- impl ----

  protected final def shouldComplete(): Boolean = isClosed(in0)

  protected final def inAvailable(): Int = bufIn0.size
}