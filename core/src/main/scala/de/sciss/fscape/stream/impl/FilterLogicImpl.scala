/*
 *  FilterLogicImpl.scala
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
import akka.stream.{Inlet, Shape}
import de.sciss.fscape.stream.BufLike

trait FilterLogicImpl[In0 >: Null <: BufLike, S <: Shape]
  extends FullInOutImpl[S] {

  _: GraphStageLogic =>

  // ---- abstract ----

  protected def in0: Inlet[In0]

  protected def inRemain: Int

  protected var bufIn0: In0

  // ---- impl ----

  protected final def inputsEnded: Boolean =
    inRemain == 0 && isClosed(in0) && !isAvailable(in0)
}