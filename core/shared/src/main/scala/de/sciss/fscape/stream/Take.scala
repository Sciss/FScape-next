/*
 *  Take.scala
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

import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.graph.ConstantL
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec
import scala.math.{max, min}

object Take {
  def head[A, E <: BufElem[A]](in: Outlet[E])(implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val length = ConstantL(1L).toLong
    apply[A, E](in = in, length = length)
  }

  def apply[A, E <: BufElem[A]](in: Outlet[E], length: OutL)(implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(length, stage.in1)
    stage.out
  }

  private final val name = "Take"

  private type Shp[E] = FanInShape2[E, BufL, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet [E](s"$name.in"    ),
      in1 = InL      (s"$name.length"),
      out = Outlet[E](s"$name.out"   )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    private[this] val hIn   = Handlers.InMain [A, E](this, shape.in0)
    private[this] val hLen  = Handlers.InLAux       (this, shape.in1)(max(0L, _))
    private[this] val hOut  = Handlers.OutMain[A, E](this, shape.out)

    private[this] var takeRemain  = -1L
    private[this] var init        = true

    protected def onDone(inlet: Inlet[_]): Unit = {
      assert (inlet == hIn.inlet)
      if (hOut.flush()) {
        completeStage()
      }
    }

    @tailrec
    protected def process(): Unit = {
      if (init) {
        if (!hLen.hasNext) return
        takeRemain  = hLen.next()
        init        = false
      }

      val remIn = hIn.available
      if (remIn == 0) return

      val remOut  = hOut.available
      val numCopy = min(remOut, min(remIn, takeRemain).toInt)
      val hasCopy = numCopy > 0
      if (hasCopy) {
        hIn.copyTo(hOut, numCopy)
        takeRemain -= numCopy
      }

      if (takeRemain == 0L || hIn.isDone) {
        if (hOut.flush()) {
          completeStage()
        }
        return
      }

      if (hasCopy) process()
    }
  }
}