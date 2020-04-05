/*
 *  HandlerImpl.scala
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

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Inlet, Outlet, Shape}
import de.sciss.fscape.logStream

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
@deprecated("Should move to using Handlers", since = "2.35.1")
final class ProcessInHandlerImpl[A, S <: Shape](in: Inlet[A], logic: FullInOutImpl[S])
  extends InHandler {

  override def toString: String = s"ProcessInHandlerImpl($in)"

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
        logStream(s"Invalid process $in")
        logic.completeStage()
      }
    }
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
@deprecated("Should move to using Handlers", since = "2.35.1")
final class AuxInHandlerImpl[A, S <: Shape](in: Inlet[A], logic: FullInOutImpl[S])
  extends InHandler {

  override def toString: String = s"AuxInHandlerImpl($in)"

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
      logStream(s"Invalid aux $in")
      logic.completeStage()
    }
  }

  logic.setInHandler(in, this)
}

@deprecated("Should move to using Handlers", since = "2.35.1")
final class ProcessOutHandlerImpl[A, S <: Shape](out: Outlet[A], logic: InOutImpl[S])
  extends OutHandler {

  override def toString: String = s"ProcessOutHandlerImpl($out)"

  def onPull(): Unit = {
    logStream(s"onPull($out)")
    logic.updateCanWrite()
    if (logic.canWrite) logic.process()
  }

  override def onDownstreamFinish(cause: Throwable): Unit = {
    logStream(s"onDownstreamFinish($out)")
    super.onDownstreamFinish(cause)
  }

  logic.setOutHandler(out, this)
}