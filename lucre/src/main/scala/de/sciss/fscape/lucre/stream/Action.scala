/*
 *  Action.scala
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
package lucre.stream

import akka.stream.{Attributes, SinkShape}
import de.sciss.fscape.lucre.UGenGraphBuilder.ActionRef
import de.sciss.fscape.stream.impl.{NodeImpl, Sink1Impl, StageImpl}
import de.sciss.fscape.stream.{BufI, Builder, Control, _}

object Action {
  def apply(trig: OutI, ref: ActionRef)(implicit b: Builder): Unit = {
    val stage0  = new Stage(ref)
    val stage   = b.add(stage0)
    b.connect(trig, stage.in)
  }

  private final val name = "Action"

  private type Shape = SinkShape[BufI]

  private final class Stage(ref: ActionRef)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new SinkShape(
      in = InI(s"$name.trig")
    )

    def createLogic(attr: Attributes) = new Logic(shape, ref)
  }

  private final class Logic(shape: Shape, ref: ActionRef)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with Sink1Impl[BufI] {

    private[this] var high0 = false

    def process(): Unit = {
      if (!canRead) {
        if (isClosed(shape.in)) {
          logStream(s"completeStage() $this")
          completeStage()
        }
        return
      }

      logStream(s"process() $this")

      val stop0   = readIns()
      val b0      = bufIn0.buf
      var h0      = high0
      var h1      = h0
      var inOffI  = 0
      while (inOffI < stop0) {
        if (inOffI < stop0) h1 = b0(inOffI) > 0
        if (h1 && !h0) {
          ref.execute(())
        }
        inOffI  += 1
        h0       = h1
      }
      high0 = h0
    }
  }
}