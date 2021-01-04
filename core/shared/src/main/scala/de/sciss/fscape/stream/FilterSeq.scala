/*
 *  FilterSeq.scala
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
import de.sciss.fscape.stream.impl.Handlers.{InDMain, InIAux, InIMain, InLMain, InMain, OutDMain, OutIMain, OutLMain, OutMain}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec

object FilterSeq {
  def apply[A, E <: BufElem[A]](in: Outlet[E], gate: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(gate, stage.in1)
    stage.out
  }

  private final val name = "FilterSeq"

  private type Shp[E] = FanInShape2[E, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet [E](s"$name.in"  ),
      in1 = InI      (s"$name.gate"),
      out = Outlet[E](s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isDouble) {
        new LogicD(shape.asInstanceOf[Shp[BufD]], layer)
      } else if (tpe.isInt) {
        new LogicI(shape.asInstanceOf[Shp[BufI]], layer)
      } else {
        assert (tpe.isLong)
        new LogicL(shape.asInstanceOf[Shp[BufL]], layer)
      }
      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class LogicD(shape: Shp[BufD], layer: Layer)(implicit ctrl: Control)
    extends Logic[Double, BufD](shape, layer) {

    protected override val hIn : InDMain  = InDMain (this, shape.in0)
    protected override val hOut: OutDMain = OutDMain(this, shape.out)

    protected def run(remIn: Int, remOut: Int): Unit = {
      var i = 0; var j = 0
      val _hIn    = hIn
      val _hGate  = hGate
      val _hOut   = hOut
      while (i < remIn && j < remOut) {
        val in    = _hIn   .next()
        val gate  = _hGate .next() > 0
        i += 1
        if (gate) {
          _hOut.next(in)
          j += 1
        }
      }
    }
  }

  private final class LogicI(shape: Shp[BufI], layer: Layer)(implicit ctrl: Control)
    extends Logic[Int, BufI](shape, layer) {

    protected override val hIn : InIMain  = InIMain (this, shape.in0)
    protected override val hOut: OutIMain = OutIMain(this, shape.out)

    protected def run(remIn: Int, remOut: Int): Unit = {
      var i = 0; var j = 0
      val _hIn    = hIn
      val _hGate  = hGate
      val _hOut   = hOut
      while (i < remIn && j < remOut) {
        val in    = _hIn   .next()
        val gate  = _hGate .next() > 0
        i += 1
        if (gate) {
          _hOut.next(in)
          j += 1
        }
      }
    }
  }

  private final class LogicL(shape: Shp[BufL], layer: Layer)(implicit ctrl: Control)
    extends Logic[Long, BufL](shape, layer) {

    protected override val hIn : InLMain  = InLMain (this, shape.in0)
    protected override val hOut: OutLMain = OutLMain(this, shape.out)

    protected def run(remIn: Int, remOut: Int): Unit = {
      var i = 0; var j = 0
      val _hIn    = hIn
      val _hGate  = hGate
      val _hOut   = hOut
      while (i < remIn && j < remOut) {
        val in    = _hIn   .next()
        val gate  = _hGate .next() > 0
        i += 1
        if (gate) {
          _hOut.next(in)
          j += 1
        }
      }
    }
  }

  private abstract class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    protected def hIn : InMain  [A, E]
    protected def hOut: OutMain [A, E]

    protected final val hGate: InIAux = InIAux(this, shape.in1)()

    final protected def onDone(inlet: Inlet[_]): Unit =
      if (hOut.flush()) completeStage()

    protected def run(remIn: Int, remOut: Int): Unit

    @tailrec
    final protected def process(): Unit = {
      val remIn = math.min(hIn.available, hGate.available)
      if (remIn == 0) return
      val remOut = hOut.available
      if (remOut == 0) return

      run(remIn, remOut)

      if (hIn.isDone) {
        if (hOut.flush()) completeStage()
        return
      }

      process()
    }
  }
}