/*
 *  Trig.scala
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

package de.sciss.fscape.stream

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import de.sciss.fscape.stream.impl.logic.FilterIn1Out1
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object Trig {
  def apply[A, Buf <: BufElem[A]](in: Outlet[Buf])
                                 (implicit b: Builder, tpe: StreamType[A, Buf]): OutI = {
    val stage0  = new Stage[A, Buf](b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "Trig"

  private type Shp[E] = FlowShape[E, BufI]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FlowShape(
      in  = Inlet[E](s"$name.in" ),
      out = OutI    (s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isDouble) {
        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer)(_ > 0.0)
      } else if (tpe.isInt) {
        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer)(_ > 0)
      } else {
        assert (tpe.isLong)
        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer)(_ > 0L)
      }
      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Logic[@specialized(Args) A, E <: BufElem[A]](shape: Shp[E], layer: Layer)(high: A => Boolean)
                                                                  (implicit ctrl: Control, tpe: StreamType[A, E])
    extends FilterIn1Out1[A, E, Int, BufI](name, layer, shape) {

    private[this] var x1 = false

    protected def run(in: Array[A], inOff0: Layer, out: Array[Int], outOff0: Int, n: Int): Unit = {
      var inOff   = inOff0
      var outOff  = outOff0
      val stop    = inOff0 + n
      var _x1     = x1
      while (inOff < stop) {
        val x0 = high(in(inOff))
        val y0 = if (x0 && !_x1) 1 else 0
        out(outOff) = y0
        _x1     = x0
        inOff  += 1
        outOff += 1
      }
      x1 = _x1
    }
  }
}