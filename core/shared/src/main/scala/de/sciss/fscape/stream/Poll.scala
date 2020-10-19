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
import de.sciss.fscape.stream.impl.shapes.SinkShape2
import de.sciss.fscape.stream.impl.{NodeImpl, PollImpl, StageImpl}

// XXX TODO --- we could use an `Outlet[String]` for label, that might be making perfect sense
object Poll {
  def apply[A, E <: BufElem[A]](in: Outlet[E], gate: OutI, label: String)
                               (implicit b: Builder, tpe: StreamType[A, E]): Unit = {
    val stage0  = new Stage[A, E](layer = b.layer, label = label)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(gate, stage.in1)
  }

  private final val name = "Poll"

  private type Shp[E] = SinkShape2[E, BufI]

  private final class Stage[A, E <: BufElem[A]](layer: Layer, label: String)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = SinkShape2(
      in0 = Inlet[E](s"$name.in"),
      in1 = InI     (s"$name.gate")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isDouble) {
        new Logic[Double, BufD](shape = shape.asInstanceOf[Shp[BufD]], layer = layer, label = label)
      } else if (tpe.isInt) {
        new Logic[Int   , BufI](shape = shape.asInstanceOf[Shp[BufI]], layer = layer, label = label)
      } else {
        assert (tpe.isLong)
        new Logic[Long  , BufL](shape = shape.asInstanceOf[Shp[BufL]], layer = layer, label = label)
      }
      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Logic[@specialized(Args) A, E <: BufElem[A]](shape: Shp[E], layer: Layer, label: String)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends PollImpl[A, E](name, layer, shape) {

    override def toString = s"$name-L($label)"

    protected def trigger(buf: Array[A], off: Int): Unit = {
      val x0 = buf(off)
      // XXX TODO --- make console selectable
      println(s"$label: $x0")
    }
  }
}