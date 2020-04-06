/*
 *  Concat.scala
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

import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

object Concat {
  def apply[A, E <: BufElem[A]](a: Outlet[E], b: Outlet[E])
                                       (implicit builder: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](builder.layer)
    val stage   = builder.add(stage0)
    builder.connect(a, stage.in0)
    builder.connect(b, stage.in1)
    stage.out
  }

  private final val name = "Concat"

  private type Shp[E] = FanInShape2[E, E, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet [E](s"$name.a"  ),
      in1 = Inlet [E](s"$name.b"  ),
      out = Outlet[E](s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                                       (implicit ctrl: Control, tpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    private[this] val hInA  = Handlers.InMain [A, E](this, shape.in0)
    private[this] val hInB  = Handlers.InMain [A, E](this, shape.in1)
    private[this] val hOut  = Handlers.OutMain[A, E](this, shape.out)

    private[this] var first = true

    protected def onDone(inlet: Inlet[_]): Unit =
      if (first) {
        if (inlet == hInA.inlet) {
          first = false
          if (checkDoneB()) return
          process()
        }
      } else {
        assert (inlet == hInB.inlet)
        checkDoneB()
      }

    private def checkDoneB(): Boolean = {
      val res = hInB.isDone
      if (res) {
        if (hOut.flush()) completeStage()
      }
      res
    }

    protected def process(): Unit = {
      while (first) {
        val rem = math.min(hInA.available, hOut.available)
        if (rem == 0) return
        hInA.copyTo(hOut, rem)
        if (hInA.isDone) {
          first = false
          if (checkDoneB()) return
        }
      }

      while (true) {
        val rem = math.min(hInB.available, hOut.available)
        if (rem == 0) return
        hInB.copyTo(hOut, rem)
        if (checkDoneB()) return
      }
    }
  }
}