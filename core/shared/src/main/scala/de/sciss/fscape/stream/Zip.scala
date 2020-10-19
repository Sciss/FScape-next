/*
 *  ZipWindow.scala
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
import de.sciss.fscape.stream.impl.Handlers._
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

object Zip {
  def apply[A, E <: BufElem[A]](a: Outlet[E], b: Outlet[E])
                               (implicit builder: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](builder.layer)
    val stage   = builder.add(stage0)
    builder.connect(a, stage.in0)
    builder.connect(b, stage.in1)
    stage.out
  }

  private final val name = "Zip"

  private type Shp[E] = FanInShape2[E, E, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet [E](s"$name.a"  ),
      in1 = Inlet [E](s"$name.b"  ),
      out = Outlet[E](s"$name.out")
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

  private abstract class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    protected def hInA: InMain  [A, E]
    protected def hInB: InMain  [A, E]
    protected def hOut: OutMain [A, E]

    final protected def onDone(inlet: Inlet[_]): Unit =
      if (hOut.flush()) completeStage()

    protected def run(n: Int): Unit

    final def process(): Unit = {
      logStream(s"process() $this")

      while (true) {
        val rem = math.min(hInA.available, math.min(hInB.available, hOut.available >>> 1))
        if (rem == 0) return

        run(rem)

        if (hInA.isDone || hInB.isDone) {
          if (hOut.flush()) completeStage()
          return
        }
      }
    }
  }

  private final class LogicD(shape: Shp[BufD], layer: Layer)
                            (implicit ctrl: Control)
    extends Logic[Double, BufD](shape, layer) {

    override protected val hInA: InDMain  = InDMain (this, shape.in0)
    override protected val hInB: InDMain  = InDMain (this, shape.in1)
    override protected val hOut: OutDMain = OutDMain(this, shape.out)

    def run(n: Int): Unit = {
      val a   = hInA
      val b   = hInB
      val out = hOut
      var i = 0
      while (i < n) {
        out.next(a.next())
        out.next(b.next())
        i += 1
      }
    }
  }

  private final class LogicI(shape: Shp[BufI], layer: Layer)
                            (implicit ctrl: Control)
    extends Logic[Int, BufI](shape, layer) {

    override protected val hInA: InIMain  = InIMain (this, shape.in0)
    override protected val hInB: InIMain  = InIMain (this, shape.in1)
    override protected val hOut: OutIMain = OutIMain(this, shape.out)

    def run(n: Int): Unit = {
      val a   = hInA
      val b   = hInB
      val out = hOut
      var i = 0
      while (i < n) {
        out.next(a.next())
        out.next(b.next())
        i += 1
      }
    }
  }

  private final class LogicL(shape: Shp[BufL], layer: Layer)
                            (implicit ctrl: Control)
    extends Logic[Long, BufL](shape, layer) {

    override protected val hInA: InLMain  = InLMain (this, shape.in0)
    override protected val hInB: InLMain  = InLMain (this, shape.in1)
    override protected val hOut: OutLMain = OutLMain(this, shape.out)

    def run(n: Int): Unit = {
      val a   = hInA
      val b   = hInB
      val out = hOut
      var i = 0
      while (i < n) {
        out.next(a.next())
        out.next(b.next())
        i += 1
      }
    }
  }
}
