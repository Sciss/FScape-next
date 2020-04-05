/*
 *  DemandHandlerImpl.scala
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

import akka.stream.stage.InHandler
import akka.stream.{Inlet, Shape}
import de.sciss.fscape.logStream

@deprecated("Should move to using Handlers", since = "2.35.1")
final class DemandProcessInHandler[A, S <: Shape](in: Inlet[A], logic: DemandInOutImpl[S])
  extends InHandler {
  def onPush(): Unit = {
    logStream(s"onPush($in)")
    logic.updateMainCanRead()
    if (logic.mainCanRead) logic.process()
  }

  override def onUpstreamFinish(): Unit = {
    logStream(s"onUpstreamFinish($in)")
    if (logic.mainInValid) {
      // logic.updateCanRead()
      logic.process()
    } // may lead to `flushOut`
    else {
      if (!logic.isInAvailable(in)) {
        logStream(s"Invalid process $in")
        logic.completeStage()
      }
    }
  }

  logic.setInHandler(in, this)
}

@deprecated("Should move to using Handlers", since = "2.35.1")
final class DemandAuxInHandler[A, S <: Shape](in: Inlet[A], logic: DemandInOutImpl[S])
  extends InHandler {

  def onPush(): Unit = {
    logStream(s"onPush($in)")
    testRead()
  }

  private[this] def testRead(): Unit = {
    logic.updateAuxCanRead()
    if (logic.auxCanRead) logic.process()
  }

  override def onUpstreamFinish(): Unit = {
    logStream(s"onUpstreamFinish($in)")
    if (logic.auxInValid || logic.isInAvailable(in)) {
      testRead()
    } else {
      println(s"Invalid aux $in")
      logic.completeStage()
    }
  }

  logic.setInHandler(in, this)
}