/*
 *  DemandFilterLogic.scala
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

import akka.stream.stage.GraphStageLogic
import akka.stream.{Inlet, Shape}

trait DemandFilterLogic[In0 >: Null <: BufLike, S <: Shape]
  extends InOutImpl[S] {

  _: GraphStageLogic =>

  // ---- abstract ----

  protected def in0: Inlet[In0]

  protected def mainInRemain: Int

  protected var bufIn0: In0

  // ---- impl ----

  protected final def inputsEnded: Boolean =
    mainInRemain == 0 && isClosed(in0) && !isAvailable(in0)
}