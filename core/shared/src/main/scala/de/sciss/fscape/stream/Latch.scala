/*
 *  Latch.scala
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

import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec
import scala.math.min

object Latch {
  def apply[A, E <: BufElem[A]](in: Outlet[E], gate: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(gate, stage.in1)
    stage.out
  }

  private final val name = "Latch"

  private type Shp[E] = FanInShape2[E, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet[E]  (s"$name.in"  ),
      in1 = InI       (s"$name.gate"),
      out = Outlet[E] (s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isInt) {
        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer)
      } else if (tpe.isLong) {
        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer)
      } else if (tpe.isDouble) {
        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer)
      } else {
        new Logic[A, E](shape, layer)
      }

      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Logic[@specialized(Args) A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                                                  (implicit ctrl: Control, tpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    private[this] val hIn   = Handlers.InMain [A, E](this, shape.in0)
    private[this] val hGate = Handlers.InIAux       (this, shape.in1)()
    private[this] val hOut  = Handlers.OutMain[A, E](this, shape.out)

    private[this] var held  = tpe.zero

    protected def onDone(inlet: Inlet[_]): Unit =
      if (hOut.flush()) completeStage()

    @tailrec
    protected def process(): Unit = {
      val rem     = min(hIn.available, min(hOut.available, hGate.available))
      if (rem == 0) return

      val in      = hIn .array
      val out     = hOut.array
      var inOff   = hIn .offset
      var outOff  = hOut.offset
      val stop0   = inOff + rem
      var v0      = held
      while (inOff < stop0) {
        val gate  = hGate.next() > 0
        if (gate) v0 = in(inOff)
        out(outOff) = v0
        inOff  += 1
        outOff += 1
      }
      held = v0
      hIn .advance(rem)
      hOut.advance(rem)

      if (hIn.isDone) {
        if (hOut.flush()) completeStage()
        return
      }
      process()
    }
  }
}