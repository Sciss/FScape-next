/*
 *  BinaryOp.scala
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
import de.sciss.fscape.stream.impl.Handlers.{InMain, OutMain}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec
import scala.math.min

object BinaryOp {

  def apply[A, E <: BufElem[A], B, F <: BufElem[B]](opName: String, op: (A, A) => B,
                                                    in1: Outlet[E], in2: Outlet[E])
                                                   (implicit b: Builder,
                                                    aTpe: StreamType[A, E],
                                                    bTpe: StreamType[B, F]): Outlet[F] = {
    val stage0  = new Stage[A, E, B, F](layer = b.layer, opName = opName, op = op)
    val stage   = b.add(stage0)
    b.connect(in1, stage.in0)
    b.connect(in2, stage.in1)
    stage.out
  }

  private final val name = "BinaryOp"

  private type Shp[E, F] = FanInShape2[E, E, F]

  private final class Stage[A, E <: BufElem[A], B, F <: BufElem[B]](layer: Layer, opName: String, op: (A, A) => B)
                                                                   (implicit ctrl: Control,
                                                                    aTpe: StreamType[A, E],
                                                                    bTpe: StreamType[B, F])
    extends StageImpl[Shp[E, F]](s"$name($opName)") {

    val shape: Shape = new FanInShape2(
      in0 = Inlet [E](s"$name.in1"),
      in1 = Inlet [E](s"$name.in2"),
      out = Outlet[F](s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _, _, _] = if (aTpe.isDouble) {
        if (bTpe.isDouble) {
          new Logic[Double, BufD, Double, BufD](
            shape.asInstanceOf[Shp[BufD, BufD]], layer, opName, op.asInstanceOf[(Double, Double)  => Double ])
        } else if (bTpe.isInt) {
          new Logic[Double, BufD, Int, BufI](
            shape.asInstanceOf[Shp[BufD, BufI]], layer, opName, op.asInstanceOf[(Double, Double)  => Int    ])
        } else {
          assert (bTpe.isLong)
          new Logic[Double, BufD, Long, BufL](
            shape.asInstanceOf[Shp[BufD, BufL]], layer, opName, op.asInstanceOf[(Double, Double)  => Long   ])
        }
      } else if (aTpe.isInt) {
        if (bTpe.isDouble) {
          new Logic[Int, BufI, Double, BufD](
            shape.asInstanceOf[Shp[BufI, BufD]], layer, opName, op.asInstanceOf[(Int   , Int   )  => Double ])
        } else if (bTpe.isInt) {
          new Logic[Int, BufI, Int, BufI](
            shape.asInstanceOf[Shp[BufI, BufI]], layer, opName, op.asInstanceOf[(Int   , Int   )  => Int    ])
        } else {
          assert (bTpe.isLong)
          new Logic[Int, BufI, Long, BufL](
            shape.asInstanceOf[Shp[BufI, BufL]], layer, opName, op.asInstanceOf[(Int   , Int   )  => Long   ])
        }
      } else {
        assert(aTpe.isLong)
        if (bTpe.isDouble) {
          new Logic[Long, BufL, Double, BufD](
            shape.asInstanceOf[Shp[BufL, BufD]], layer, opName, op.asInstanceOf[(Long  , Long  )  => Double ])
        } else if (bTpe.isInt) {
          new Logic[Long, BufL, Int, BufI](
            shape.asInstanceOf[Shp[BufL, BufI]], layer, opName, op.asInstanceOf[(Long  , Long  )  => Int    ])
        } else {
          assert (bTpe.isLong)
          new Logic[Long, BufL, Long, BufL](
            shape.asInstanceOf[Shp[BufL, BufL]], layer, opName, op.asInstanceOf[(Long  , Long  )  => Long   ])
        }
      }
      res.asInstanceOf[Logic[A, E, B, F]]
    }
  }

  private final class Logic[
    @specialized(Args) A, E <: BufElem[A],
    @specialized(Args) B, F <: BufElem[B]](shape: Shp[E, F], layer: Layer, opName: String, op: (A, A) => B)
                                          (implicit ctrl: Control, aTpe: StreamType[A, E], bTpe: StreamType[B, F])
    extends Handlers(s"$name($opName)", layer, shape) {

    private[this] val hA  : InMain  [A, E] = InMain [A, E](this, shape.in0)
    private[this] val hB  : InMain  [A, E] = InMain [A, E](this, shape.in1)
    private[this] val hOut: OutMain [B, F] = OutMain[B, F](this, shape.out)

    private[this] var aVal: A = _
    private[this] var bVal: A = _
    private[this] var aValid = false
    private[this] var bValid = false

    protected def onDone(inlet: Inlet[_]): Unit = {
      val live = if (inlet == hA.inlet) {
        aValid && !hB.isDone
      } else {
        assert (inlet == hB.inlet)
        bValid && !hA.isDone
      }
      if (live) process() else if (hOut.flush()) completeStage()
    }

    @tailrec
    protected def process(): Unit = {
      val remOut = hOut.available
      if (remOut == 0) return

      if (hB.isDone) {
        val remA = hA.available
        if (remA == 0) return

        val bv      = bVal
        val rem     = min(remA, remOut)
        val a       = hA  .array
        var offA    = hA  .offset
        val out     = hOut.array
        var offOut  = hOut.offset
        val stop    = offOut + rem
        var av: A   = null.asInstanceOf[A]
        while (offOut < stop) {
          av          = a(offA)
          out(offOut) = op(av, bv)
          offA   += 1
          offOut += 1
        }
        hA  .advance(rem)
        hOut.advance(rem)
        aVal    = av
        aValid  = true
        if (hA.isDone) {
          if (hOut.flush()) completeStage()
        } else {
          process()
        }

      } else if (hA.isDone) {
        val remB = hB.available
        if (remB == 0) return

        val av      = aVal
        val rem     = min(remB, remOut)
        val b       = hB  .array
        var offB    = hB  .offset
        val out     = hOut.array
        var offOut  = hOut.offset
        val stop    = offOut + rem
        var bv: A   = null.asInstanceOf[A]
        while (offOut < stop) {
          bv          = b(offB)
          out(offOut) = op(av, bv)
          offB   += 1
          offOut += 1
        }
        hB  .advance(rem)
        hOut.advance(rem)
        bVal    = bv
        bValid  = true
        if (hB.isDone) {
          if (hOut.flush()) completeStage()
        } else {
          process()
        }

      } else {
        val remIn = min(hA.available, hB.available)
        if (remIn == 0) return

        val rem     = min(remIn, remOut)
        val a       = hA  .array
        var offA    = hA  .offset
        val b       = hB  .array
        var offB    = hB  .offset
        val out     = hOut.array
        var offOut  = hOut.offset
        val stop    = offOut + rem
        var av: A   = null.asInstanceOf[A]
        var bv: A   = null.asInstanceOf[A]
        while (offOut < stop) {
          av          = a(offA)
          bv          = b(offB)
          out(offOut) = op(av, bv)
          offA   += 1
          offB   += 1
          offOut += 1
        }
        hA  .advance(rem)
        hB  .advance(rem)
        hOut.advance(rem)
        aVal    = av
        bVal    = bv
        bValid  = true
        aValid  = true
        if (hA.isDone && hB.isDone) {
          if (hOut.flush()) completeStage()
        } else {
          process()
        }
      }
    }
  }
}