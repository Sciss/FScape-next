/*
 *  Frames.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape}
import de.sciss.fscape.stream.impl.{StageImpl, NodeImpl}

object Frames {
  def apply(in: OutA)(implicit b: Builder): OutL = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "Frames"

  private type Shape = FlowShape[BufLike, BufL]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FlowShape(
      in  = InA (s"$name.in" ),
      out = OutL(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape) with InHandler with OutHandler {

    setHandler(shape.in , this)
    setHandler(shape.out, this)

    private[this] var framesRead = 0L

    override def preStart(): Unit =
      pull(shape.in)

    // ---- handlers ----

    def onPush(): Unit = if (isAvailable(shape.out)) process()
    def onPull(): Unit = if (isAvailable(shape.in )) process()

    private def process(): Unit = {
      val bufIn   = grab(shape.in)
      val bufOut  = ctrl.borrowBufL()
      val sz      = bufIn.size
      val arr     = bufOut.buf
      var i       = 0
      var j       = framesRead
      while (i < sz) {
        j += 1
        arr(i) = j
        i += 1
      }
      framesRead  = j
      bufIn.release()
      bufOut.size = sz
      push(shape.out, bufOut)
      tryPull(shape.in)
    }
  }
}