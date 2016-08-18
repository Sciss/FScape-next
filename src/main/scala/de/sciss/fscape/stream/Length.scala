/*
 *  Length.scala
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

import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape}
import de.sciss.fscape.stream.impl.{StageImpl, StageLogicImpl}

object Length {
  def apply(in: OutD)(implicit b: Builder): OutL = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "Length"

  private type Shape = FlowShape[BufD, BufL]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FlowShape(
      in  = InD (s"$name.in" ),
      out = OutL(s"$name.out")
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape) with InHandler with OutHandler {

    setHandler(shape.in , this)
    setHandler(shape.out, this)

    private[this] var framesRead = 0L

    override def preStart(): Unit =
      pull(shape.in)

    // ---- InHandler ----

    def onPush(): Unit = {
      val buf = grab(shape.in)
      framesRead += buf.size
      buf.release()
      tryPull(shape.in)
    }

    override def onUpstreamFinish(): Unit = if (isAvailable(shape.out)) writeAndFinish()

    // ---- OutHandler ----

    def onPull(): Unit = if (isClosed(shape.in)) writeAndFinish()

    private def writeAndFinish(): Unit = {
      val buf     = ctrl.borrowBufL()
      buf.size    = 1
      buf.buf(0)  = framesRead
      push(shape.out, buf)
      completeStage()
    }
  }
}