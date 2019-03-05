/*
 *  DemandHandlerImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
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

import akka.stream.{Inlet, Shape}
import akka.stream.stage.InHandler

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
        println(s"Invalid process $in")
        logic.completeStage()
      }
    }
  }

  logic.setInHandler(in, this)
}

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