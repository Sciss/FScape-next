/*
 *  Differentiate.scala
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

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import de.sciss.fscape.stream.impl.logic.FilterIn1Out1
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object Differentiate {
  def apply[A, E <: BufElem[A]](in: Outlet[E])(implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "Differentiate"

  private type Shp[E] = FlowShape[E, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FlowShape(
      in  = Inlet [E](s"$name.in"  ),
      out = Outlet[E](s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isDouble) {
        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer)(_ - _)
      } else if (tpe.isInt) {
        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer)(_ - _)
      } else {
        assert (tpe.isLong)
        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer)(_ - _)
      }
      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Logic[@specialized(Args) A, E <: BufElem[A]](shape: Shp[E], layer: Layer)(diff: (A, A) => A)
                                                                  (implicit ctrl: Control, tpe: StreamType[A, E])
    extends FilterIn1Out1[A, E, A, E](name, layer, shape) {

    private[this] var xPrev: A = tpe.zero

    protected def run(in: Array[A], inOff0: Int, out: Array[A], outOff0: Int, n: Int): Unit = {
      val stop    = inOff0 + n
      var inOff   = inOff0
      var outOff  = outOff0
      var x1      = xPrev
      while (inOff < stop) {
        val x0      = in(inOff)
        val y0      = diff(x0, x1)
        out(outOff) = y0
        x1          = x0
        inOff      += 1
        outOff     += 1
      }
      xPrev = x1
    }
  }
}