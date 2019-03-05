/*
 *  Poll.scala
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

import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{NodeImpl, PollImpl, SinkShape2, StageImpl}

// XXX TODO --- we could use an `Outlet[String]`, that might be making perfect sense
object Poll {
  def apply(in: Outlet[BufLike], trig: OutI, label: String)(implicit b: Builder): Unit = {
    // println(s"Poll($in, $trig, $label)")
    val stage0  = new Stage(label = label)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(trig, stage.in1)
  }

  private final val name = "Poll"

  private type Shape = SinkShape2[BufLike, BufI]

  private final class Stage(label: String)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = SinkShape2(
      in0 = Inlet[BufLike](s"$name.in"),
      in1 = InI(s"$name.trig")
    )

    def createLogic(attr: Attributes) = new Logic(label = label, shape = shape)
  }

  private final class Logic(label: String, shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with PollImpl[BufLike] {

    override def toString = s"$name-L($label)"

    protected def trigger(buf: BufLike, off: Int): Unit = {
      val x0 = buf.buf(off)
      // XXX TODO --- make console selectable
      println(s"$label: $x0")
    }
  }
}