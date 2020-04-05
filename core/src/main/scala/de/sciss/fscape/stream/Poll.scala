/*
 *  Poll.scala
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

import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{NodeImpl, PollImpl, SinkShape2, StageImpl}

// XXX TODO --- we could use an `Outlet[String]` for label, that might be making perfect sense
object Poll {
  def apply(in: Outlet[BufLike], gate: OutI, label: String)(implicit b: Builder): Unit = {
    val stage0  = new Stage(layer = b.layer, label = label)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(gate, stage.in1)
  }

  private final val name = "Poll"

  private type Shp = SinkShape2[BufLike, BufI]

  private final class Stage(layer: Layer, label: String)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = SinkShape2(
      in0 = Inlet[BufLike](s"$name.in"),
      in1 = InI(s"$name.gate")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape = shape, layer = layer, label = label)
  }

  private final class Logic(shape: Shp, layer: Layer, label: String)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with PollImpl[BufLike] {

    override def toString = s"$name-L($label)"

    protected def trigger(buf: BufLike, off: Int): Unit = {
      val x0 = buf.buf(off)
      // XXX TODO --- make console selectable
      println(s"$label: $x0")
    }
  }
}