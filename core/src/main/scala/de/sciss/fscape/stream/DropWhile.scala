/*
 *  DropWhile.scala
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
import Handlers._

object DropWhile {
  def apply[A, Buf >: Null <: BufElem[A]](in: Outlet[Buf], p: OutI)
                                         (implicit b: Builder, aTpe: StreamType[A, Buf]): Outlet[Buf] = {
    val stage0  = new Stage[A, Buf](b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in0)
    b.connect(p , stage.in1)
    stage.out
  }

  private final val name = "DropWhile"

  private type Shape[A, Buf >: Null <: BufElem[A]] = FanInShape2[Buf, BufI, Buf]

  private final class Stage[A, Buf >: Null <: BufElem[A]](layer: Layer)
                                                         (implicit ctrl: Control, aTpe: StreamType[A, Buf])
    extends StageImpl[Shape[A, Buf]](name) {

    val shape = new FanInShape2(
      in0 = Inlet [Buf](s"$name.in" ),
      in1 = InI        (s"$name.p"  ),
      out = Outlet[Buf](s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[DropWhile.Shape[A, Buf]] = new Logic(shape, layer)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[A, E], layer: Layer)
                                                       (implicit ctrl: Control, aTpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    private[this] val hIn   : InMain [A, E] = InMain [A, E](this, shape.in0)
    private[this] val hPred : InIAux        = InIAux       (this, shape.in1)()
    private[this] val hOut  : OutMain[A, E] = OutMain[A, E](this, shape.out)
    private[this] var gate  = true

    protected def onDone(inlet: Inlet[_]): Unit = {
      assert (inlet == shape.in0)
      if (hOut.flush()) completeStage()
    }

    private def checkInDone(): Boolean = {
      val res = hIn.isDone && hOut.flush()
      if (res) completeStage()
      res
    }

    def process(): Unit = {
      logStream(s"process() $this")

      if (gate) {
        while (gate) {
          val remIn   = math.min(hIn.available, hPred.available)
          val remOut  = hOut.available
          if (remIn == 0 || remOut == 0) return
          var count   = 0
          var _gate   = true
          while (_gate && count < remIn && count < remOut) {
            _gate = hPred.next() > 0
            if (_gate) {
              count += 1
            }
          }
          if (count > 0) {
            hIn.skip(count)
            if (checkInDone()) return
          }
          gate = _gate
        }
      }

      // always enter here -- `gate` must be `false` now
      while ({
        val len = math.min(hIn.available, hOut.available)
        (len > 0) && {
          hIn.copyTo(hOut, len)
          !checkInDone()
        }
      }) ()
    }
  }
}