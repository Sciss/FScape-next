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

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _, _, _] = if (aTpe.isDouble) {
        if (bTpe.isDouble) {
          new Logic[Double, BufD, Double, BufD](
            shape.asInstanceOf[Shp[BufD, BufD]], layer, opName, op.asInstanceOf[Double  => Double ])
        } else if (bTpe.isInt) {
          new Logic[Double, BufD, Int, BufI](
            shape.asInstanceOf[Shp[BufD, BufI]], layer, opName, op.asInstanceOf[Double  => Int    ])
        } else {
          assert (bTpe.isLong)
          new Logic[Double, BufD, Long, BufL](
            shape.asInstanceOf[Shp[BufD, BufL]], layer, opName, op.asInstanceOf[Double  => Long   ])
        }
      } else if (aTpe.isInt) {
        if (bTpe.isDouble) {
          new Logic[Int, BufI, Double, BufD](
            shape.asInstanceOf[Shp[BufI, BufD]], layer, opName, op.asInstanceOf[Int     => Double ])
        } else if (bTpe.isInt) {
          new Logic[Int, BufI, Int, BufI](
            shape.asInstanceOf[Shp[BufI, BufI]], layer, opName, op.asInstanceOf[Int     => Int    ])
        } else {
          assert (bTpe.isLong)
          new Logic[Int, BufI, Long, BufL](
            shape.asInstanceOf[Shp[BufI, BufL]], layer, opName, op.asInstanceOf[Int     => Long   ])
        }
      } else {
        assert(aTpe.isLong)
        if (bTpe.isDouble) {
          new Logic[Long, BufL, Double, BufD](
            shape.asInstanceOf[Shp[BufL, BufD]], layer, opName, op.asInstanceOf[Long    => Double ])
        } else if (bTpe.isInt) {
          new Logic[Long, BufL, Int, BufI](
            shape.asInstanceOf[Shp[BufL, BufI]], layer, opName, op.asInstanceOf[Long    => Int    ])
        } else {
          assert (bTpe.isLong)
          new Logic[Long, BufL, Long, BufL](
            shape.asInstanceOf[Shp[BufL, BufL]], layer, opName, op.asInstanceOf[Long    => Long   ])
        }
      }
      res.asInstanceOf[Logic[A, E, B, F]]
    }
  }

  private final class Logic[
    @specialized(Args) A, E <: BufElem[A],
    @specialized(Args) B, F <: BufElem[B]](shape: Shp[E, F], layer: Layer, opName: String, op: A => B)
                                          (implicit control: Control, aTpe: StreamType[A, E], bTpe: StreamType[B, F])
    extends Handlers(s"$name($opName)", layer, shape) {

    private[this] val hIn : InMain  [A, E] = InMain  [A, E](this, shape.in )
    private[this] val hOut: OutMain [B, F] = OutMain [B, F](this, shape.out)

    private def run(a: Array[A], ai0: Int, b: Array[B], bi0: Int, stop: Int): Unit = {
      var ai = ai0
      var bi = bi0
      while (ai < stop) {
        b(bi) = op(a(ai))
        ai += 1
        bi += 1
      }
    }

    @tailrec
    protected def process(): Unit = {
      val rem = math.min(hIn.available, hOut.available)
      if (rem == 0) return

      val a     = hIn .array
      val ai    = hIn .offset
      val b     = hOut.array
      val bi    = hOut.offset
      val stop  = ai + rem
      run(a = a, ai0 = ai, b = b, bi0 = bi, stop = stop)
      hIn .advance(rem)
      hOut.advance(rem)

      if (hIn.isDone) {
        if (hOut.flush()) completeStage()
        return
      }

      process()
    }

    protected def onDone(inlet: Inlet[_]): Unit =
      if (hOut.flush()) {
        completeStage()
      }
  }
}