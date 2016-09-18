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

// XXX TODO --- most likely we don't need the
// complicated `ProcessInHandlerImpl` can replace it
// everywhere with `EquivalentInHandlerImpl`.
// I.e. we assume the logic will always look for `shouldComplete`.

/** A handler that when pushed, calls `updateCanRead`,
  * and if this is `true`, calls `process.` If the
  * inlet is closed, it will call `process` if the
  * crucial inputs had been pushed before. In that
  * case the logic is responsible for closing the stage.
  * If no valid input had been seen, an `onUpstreamFinish`
  * leads to stage completion.
  *
  * This function is used for a "hot" inlet.
  */
final class ProcessInHandlerImpl[A, S <: Shape](in: Inlet[A], logic: InOutImpl[S])
  extends InHandler {
  def onPush(): Unit = {
    logStream(s"onPush($in)")
    logic.updateCanRead()
    if (logic.canRead) logic.process()
  }

  override def onUpstreamFinish(): Unit = {
    logStream(s"onUpstreamFinish($in)")
    if (logic.inValid) {
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

/** A handler that when pushed, calls `updateCanRead`,
  * and if this is `true`, calls `process.` If the
  * inlet is closed, it will call `updateCanRead` and
  * `process` The logic is responsible for closing the stage
  * if this was the last one among the "hot" inlets.
  *
  * This function is used for one "hot" inlet among
  * multiple "hot" peers.
  */
final class EquivalentInHandlerImpl[A, S <: Shape](in: Inlet[A], logic: InOutImpl[S])
  extends InHandler {
  def onPush(): Unit = {
    logStream(s"onPush($in)")
    logic.updateCanRead()
    if (logic.canRead) logic.process()
  }

  override def onUpstreamFinish(): Unit = {
    logStream(s"onUpstreamFinish($in)")
    logic.updateCanRead()
    logic.process()
  }

  logic.setInHandler(in, this)
}

/** A handler that when pushed, calls `updateCanRead`,
  * and if this is `true`, calls `process.` If the
  * inlet is closed, and the logic's input was valid
  * or there was a not-yet-pulled content pending on
  * this inlet, it will call `process`.
  * If no valid input had been seen, an `onUpstreamFinish`
  * leads to stage completion.
  *
  * This function is used for an "auxiliary" inlet.
  */
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