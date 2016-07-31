/*
 *  GenIn0Impl.scala
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

import akka.stream.stage.GraphStageLogic
import akka.stream.{Outlet, SourceShape}

/** Building block for generators with `SourceShape` type graph stage logic.
  * A generator keeps producing output until down-stream is closed.
  */
trait GenIn0Impl[Out >: Null <: BufLike]
  extends Out1LogicImpl[Out, SourceShape[Out]] {
  _: GraphStageLogic =>

  // ---- impl ----

  protected final var bufOut0: Out = _

  protected final def out0: Outlet[Out] = shape.out

  final def canRead: Boolean = true
  final def inValid: Boolean = true

  override def postStop(): Unit = {
    freeInputBuffers()
    freeOutputBuffers()
  }

  protected final def readIns(): Int = ctrl.blockSize

  protected final def freeInputBuffers(): Unit = ()

  protected final def freeOutputBuffers(): Unit =
    if (bufOut0 != null) {
      bufOut0.release()
      bufOut0 = null
    }

  final def updateCanRead(): Unit = ()

  new ProcessOutHandlerImpl(shape.out, this)
}

trait GenIn0DImpl extends GenIn0Impl[BufD] with Out1DoubleImpl[SourceShape[BufD]] {
  _: GraphStageLogic =>
}