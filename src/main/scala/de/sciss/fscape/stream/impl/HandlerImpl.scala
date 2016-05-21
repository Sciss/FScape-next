/*
 *  HandlerImpl.scala
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

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Inlet, Outlet, Shape}

final class ProcessInHandlerImpl[A, S <: Shape](in: Inlet[A], logic: InOutImpl[S])
  extends InHandler {
  def onPush(): Unit = {
    logic.updateCanRead()
    if (logic.canRead) logic.process()
  }

  override def onUpstreamFinish(): Unit =
    if (logic.inValid) logic.process() // may lead to `flushOut`
    else {
      if (!logic.isInAvailable(in)) {
        println(s"inValid Process $logic")
        logic.completeStage()
      }
    }

  logic.setInHandler(in, this)
}

final class AuxInHandlerImpl[A, S <: Shape](in: Inlet[A], logic: InOutImpl[S])
  extends InHandler {

  def onPush(): Unit = {
    logic.updateCanRead()
    if (logic.canRead) logic.process()
  }

  override def onUpstreamFinish(): Unit =
    if (!logic.inValid && !logic.isInAvailable(in)) {
       println(s"inValid Aux $logic")
       logic.completeStage()
    }

  logic.setInHandler(in, this)
}

final class ProcessOutHandlerImpl[A, S <: Shape](out: Outlet[A], logic: InOutImpl[S])
  extends OutHandler {

  def onPull(): Unit = logic.process()

  logic.setOutHandler(out, this)
}