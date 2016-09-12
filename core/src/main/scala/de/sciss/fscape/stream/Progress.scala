/*
 *  Progress.scala
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

import akka.stream.Attributes
import de.sciss.fscape.stream.impl.{NodeImpl, PollImpl, SinkShape2, StageImpl}

object Progress {
  def apply(in: OutD, trig: OutI, label: String)(implicit b: Builder): Unit = {
    val stage0  = new Stage(label = label)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(trig, stage.in1)
  }

  private final val name = "Progress"

  private type Shape = SinkShape2[BufD, BufI]

  private final class Stage(label: String)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = SinkShape2(
      in0 = InD(s"$name.in"),
      in1 = InI(s"$name.trig")
    )

    def createLogic(attr: Attributes) = new Logic(label = label, shape = shape)
  }

  private final class Logic(label: String, shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with PollImpl[BufD] {

    private[this] val key = ctrl.mkProgress(label)

    override def toString = s"$name-L($label)"

    protected def trigger(buf: BufD, off: Int): Unit = {
      val frac = buf.buf(off)
      ctrl.setProgress(key, frac)
    }
  }
}