/*
 *  Out1Impl.scala
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

import akka.stream.Shape
import akka.stream.stage.GraphStageLogic
import de.sciss.fscape.stream.{BufD, BufI, BufL}

@deprecated("Should move to using Handlers", since = "2.35.1")
trait Out1DoubleImpl[S <: Shape] extends InOutImpl[S] {
  _: GraphStageLogic =>

  protected final def allocOutBuf0(): BufD = control.borrowBufD()
}

@deprecated("Should move to using Handlers", since = "2.35.1")
trait Out1IntImpl[S <: Shape] extends InOutImpl[S] {
  _: GraphStageLogic =>

  protected final def allocOutBuf0(): BufI = control.borrowBufI()
}

@deprecated("Should move to using Handlers", since = "2.35.1")
trait Out1LongImpl[S <: Shape] extends InOutImpl[S] {
  _: GraphStageLogic =>

  protected final def allocOutBuf0(): BufL = control.borrowBufL()
}
