/*
 *  IfThen.scala
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

import akka.stream.{Attributes, SinkShape}
import de.sciss.fscape.stream.impl.{NodeImpl, Sink1Impl, StageImpl}

object IfThen {
  def apply(cond: OutI, branchLayer: Layer)(implicit b: Builder): Unit = {
    val stage0  = new Stage(b.layer, branchLayer = branchLayer)
    val stage   = b.add(stage0)
    b.connect(cond, stage.in)
  }

  private final val name = "IfThen"

  private type Shape = SinkShape[BufI]

  private final class Stage(thisLayer: Layer, branchLayer: Layer)(implicit ctrl: Control)
    extends StageImpl[Shape](name) {

    val shape: Shape = SinkShape(
      in = InI(s"$name.cond")
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer = thisLayer, branchLayer = branchLayer)
  }

  private final class Logic(shape: Shape, layer: Layer, branchLayer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with Sink1Impl[BufI] {

//    private[this] var init = false
//
//    protected override def stopped(): Unit = {
//      super.stopped()
//      if (init) ctrl.cancelLayer(branchLayer)
//    }

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
      if (stop0 > 0) {
        val res: Int  = b0(0)
        val cond      = res > 0
        if (cond) {
          ctrl.launchLayer(branchLayer)
        } else {
          ctrl.cancelLayer(branchLayer)
        }
        completeStage()
      }
    }
  }
}
