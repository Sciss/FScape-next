/*
 *  Out1Impl.scala
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

import akka.stream.Shape
import akka.stream.stage.GraphStageLogic

trait Out1DoubleImpl[S <: Shape] extends InOutImpl[S] {
  _: GraphStageLogic =>

  protected final def allocOutBuf0(): BufD = ctrl.borrowBufD()
}

trait Out1IntImpl[S <: Shape] extends InOutImpl[S] {
  _: GraphStageLogic =>

  protected final def allocOutBuf0(): BufI = ctrl.borrowBufI()
}

trait Out1LongImpl[S <: Shape] extends InOutImpl[S] {
  _: GraphStageLogic =>

  protected final def allocOutBuf0(): BufL = ctrl.borrowBufL()
}
