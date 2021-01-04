/*
 *  HandlerImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.stream.impl.deprecated

import akka.stream.stage.OutHandler
import akka.stream.{Outlet, Shape}
import de.sciss.fscape.Log.{stream => logStream}

@deprecated("Should move to using Handlers", since = "2.35.1")
final class ProcessOutHandlerImpl[A, S <: Shape](out: Outlet[A], logic: InOutImpl[S])
  extends OutHandler {

  override def toString: String = s"ProcessOutHandlerImpl($out)"

  def onPull(): Unit = {
    logStream.debug(s"onPull($out)")
    logic.updateCanWrite()
    if (logic.canWrite) logic.process()
  }

  override def onDownstreamFinish(cause: Throwable): Unit = {
    logStream.info(s"onDownstreamFinish($out)")
    super.onDownstreamFinish(cause)
  }

  logic.setOutHandler(out, this)
}