/*
 *  Out1LogicImpl.scala
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

import akka.stream.stage.GraphStageLogic
import akka.stream.{Outlet, Shape}
import de.sciss.fscape.stream.BufLike

@deprecated("Should move to using Handlers", since = "2.35.1")
trait Out1LogicImpl[Out <: BufLike, S <: Shape] extends InOutImpl[S] {

  this: GraphStageLogic =>

  protected def allocOutBuf0(): Out

  protected var bufOut0: Out
  
  protected def out0: Outlet[Out]

  private[this] var _canWrite = false

  final def canWrite: Boolean = _canWrite

  final def updateCanWrite(): Unit =
    _canWrite = isAvailable(out0)

  protected final def writeOuts(outOff: Int): Unit = {
    if (outOff > 0) {
      bufOut0.size = outOff
      push(out0, bufOut0)
    } else {
      bufOut0.release()
    }
    bufOut0   = null.asInstanceOf[Out]
    _canWrite = false
  }

  protected final def allocOutputBuffers(): Int = {
    bufOut0 = allocOutBuf0()
    bufOut0.size
  }
}