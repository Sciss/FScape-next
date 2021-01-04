/*
 *  Progress.scala
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

package de.sciss.fscape
package stream

import akka.stream.Attributes
import de.sciss.fscape.stream.impl.shapes.SinkShape2
import de.sciss.fscape.stream.impl.{NodeImpl, PollImpl, StageImpl}

object Progress {
  def apply(in: OutD, trig: OutI, label: String)(implicit b: Builder): Unit = {
    val stage0  = new Stage(layer = b.layer, label = label)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(trig, stage.in1)
  }

  private final val name = "Progress"

  private type Shp = SinkShape2[BufD, BufI]

  private final class Stage(layer: Layer, label: String)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = SinkShape2(
      in0 = InD(s"$name.in"),
      in1 = InI(s"$name.trig")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(layer = layer, shape = shape, label = label)
  }

  private final class Logic(shape: Shp, layer: Layer, label: String)(implicit ctrl: Control)
    extends PollImpl[Double, BufD](name, layer, shape) {

    private[this] val key = ctrl.mkProgress(label)

    override def toString = s"$name-L($label)"

    protected def trigger(buf: Array[Double], off: Int): Unit = {
      val fraction = buf(off)
      ctrl.setProgress(key, fraction)
    }
  }
}