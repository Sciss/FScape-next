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

package de.sciss.fscape
package stream
package impl

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Inlet, Outlet, Shape}

final class ProcessInHandlerImpl[A, S <: Shape](in: Inlet[A], logic: InOutImpl[S])
  extends InHandler {
  def onPush(): Unit = {
    logStream(s"onPush($in)")
    logic.updateCanRead()
    if (logic.canRead) logic.process()
  }

  override def onUpstreamFinish(): Unit = {
    logStream(s"onUpstreamFinish($in)")
    if (logic.inValid) logic.process() // may lead to `flushOut`
    else {
      if (!logic.isInAvailable(in)) {
        println(s"Invalid process $in")
        logic.completeStage()
      }
    }
  }

  logic.setInHandler(in, this)
}

final class AuxInHandlerImpl[A, S <: Shape](in: Inlet[A], logic: InOutImpl[S])
  extends InHandler {

  def onPush(): Unit = {
    logStream(s"onPush($in)")
    testRead()
  }

  private[this] def testRead(): Unit = {
    logic.updateCanRead()
    if (logic.canRead) logic.process()
  }

  override def onUpstreamFinish(): Unit = {
    logStream(s"onUpstreamFinish($in)")
    if (logic.inValid || logic.isInAvailable(in)) {
      testRead()
    } else {
      println(s"Invalid aux $in")
      logic.completeStage()
    }
  }

  logic.setInHandler(in, this)
}

final class ProcessOutHandlerImpl[A, S <: Shape](out: Outlet[A], logic: InOutImpl[S])
  extends OutHandler {

  def onPull(): Unit = {
    logStream(s"onPull($out)")
    logic.updateCanWrite()
    if (logic.canWrite) logic.process()
  }

  override def onDownstreamFinish(): Unit = {
    logStream(s"onDownstreamFinish($out)")
    super.onDownstreamFinish()
  }

  logic.setOutHandler(out, this)
}