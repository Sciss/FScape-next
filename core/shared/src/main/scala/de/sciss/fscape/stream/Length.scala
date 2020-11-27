/*
 *  Length.scala
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

package de.sciss.fscape
package stream

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape}
import de.sciss.fscape.Log.{stream => logStream}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object Length {
  def apply(in: OutA)(implicit b: Builder): OutL = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "Length"

  private type Shp = FlowShape[BufLike, BufL]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FlowShape(
      in  = InA (s"$name.in" ),
      out = OutL(s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with InHandler with OutHandler {

    setHandler(shape.in , this)
    setHandler(shape.out, this)

    private[this] var framesRead = 0L

    // ---- InHandler ----

    def onPush(): Unit = {
      val buf = grab(shape.in)
      framesRead += buf.size
      logStream.debug(s"onPush() $this: $framesRead")
      buf.release()
      tryPull(shape.in)
    }

    override def onUpstreamFinish(): Unit = {
      logStream.info(s"onUpstreamFinish() $this")
      if (isAvailable(shape.out)) {
        writeAndFinish()
      }
    }

    // ---- OutHandler ----

    def onPull(): Unit = {
      logStream.debug(s"onPull() $this")
      if (isClosed(shape.in) /*&& !isAvailable(shape.in)*/) writeAndFinish()
    }

    override def onDownstreamFinish(cause: Throwable): Unit = {
      logStream.info(s"onDownstreamFinish() $this")
      super.onDownstreamFinish(cause)
    }

    private def writeAndFinish(): Unit = {
      logStream.info(s"writeAndFinish() $this: $framesRead")
      val buf     = ctrl.borrowBufL()
      buf.size    = 1
      buf.buf(0)  = framesRead
      push(shape.out, buf)
      completeStage()
    }
  }
}