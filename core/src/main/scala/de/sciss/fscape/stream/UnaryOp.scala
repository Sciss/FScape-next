/*
 *  UnaryOp.scala
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

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import de.sciss.fscape.stream.impl.Handlers._
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec

object UnaryOp {
  def apply[A, E <: BufElem[A], B, F <: BufElem[B]](opName: String, op: A => B, in: Outlet[E])
                                                   (implicit b: Builder, aTpe: StreamType[A, E],
                                                    bTpe: StreamType[B, F]): Outlet[F] = {
    val stage0  = new Stage[A, E, B, F](b.layer, opName, op)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "UnaryOp"

  private type Shp[E, F] = FlowShape[E, F]

  private final class Stage[A, E <: BufElem[A], B, F <: BufElem[B]](layer: Layer, opName: String,
                                                                    op: A => B)
                                                                   (implicit ctrl: Control, aTpe: StreamType[A, E],
                                                                    bTpe: StreamType[B, F])
    extends StageImpl[Shp[E, F]](s"$name($opName)") {

    val shape: Shape = new FlowShape(
      in  = Inlet [E](s"$name.in" ),
      out = Outlet[F](s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic[A, E, B, F](shape, layer, opName, op)
  }

  private final class Logic[@specialized(Args) A, E <: BufElem[A],
    @specialized(Args) B, F <: BufElem[B]](shape: Shp[E, F], layer: Layer, opName: String, op: A => B)
                                          (implicit ctrl: Control, aTpe: StreamType[A, E], bTpe: StreamType[B, F])
    extends Handlers(s"$name($opName)", layer, shape) {

    private[this] val hIn : InMain  [A, E] = InMain [A, E](this, shape.in )
    private[this] val hOut: OutMain [B, F] = OutMain[B, F](this, shape.out)

    @tailrec
    protected def process(): Unit = {
      val rem = math.min(hIn.available, hOut.available)
      if (rem == 0) return

      val a     = hIn .array
      var ai    = hIn .offset
      val b     = hOut.array
      var bi    = hOut.offset
      val stop  = ai + rem
      while (ai < stop) {
        b(bi) = op(a(ai))
        ai += 1
        bi += 1
      }
      hIn .advance(rem)
      hOut.advance(rem)
      process()
    }

    protected def onDone(inlet: Inlet[_]): Unit =
      if (hOut.flush()) {
        completeStage()
      }
  }
}