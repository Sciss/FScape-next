/*
 *  TakeWhile.scala
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

object TakeWhile {
  def apply[A, Buf >: Null <: BufElem[A]](in: Outlet[Buf], p: OutI)
                                         (implicit b: Builder, aTpe: StreamType[A, Buf]): Outlet[Buf] = {
    val stage0  = new Stage[A, Buf](b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in0)
    b.connect(p , stage.in1)
    stage.out
  }

  private final val name = "TakeWhile"

  private type Shape[A, Buf >: Null <: BufElem[A]] = FanInShape2[Buf, BufI, Buf]

  private final class Stage[A, Buf >: Null <: BufElem[A]](layer: Layer)
                                                         (implicit ctrl: Control, aTpe: StreamType[A, Buf])
    extends StageImpl[Shape[A, Buf]](name) {

    val shape = new FanInShape2(
      in0 = Inlet [Buf](s"$name.in" ),
      in1 = InI        (s"$name.p"  ),
      out = Outlet[Buf](s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[TakeWhile.Shape[A, Buf]] = new Logic(shape, layer)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[A, E], layer: Layer)
                                                       (implicit ctrl: Control, aTpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    private[this] val hIn   = new Handlers.InMain [A, E](this, shape.in0)()
    private[this] val hPred = new Handlers.InIAux       (this, shape.in1)()
    private[this] val hOut  = new Handlers.OutMain[A, E](this, shape.out)
    private[this] var gate  = true

    protected def onDone(inlet: Inlet[_]): Unit = {
      assert (inlet == shape.in0)
      if (hOut.flush()) completeStage()
    }

    def process(): Unit = {
      logStream(s"process() $this")

      if (gate) {
        val remIn   = math.min(hIn.available, hPred.available)
        val remOut  = hOut.available
        if (remIn == 0 || remOut == 0) return
        var copy    = 0
        var _gate   = true
        while (_gate && copy < remIn && copy < remOut) {
          _gate = hPred.next() > 0
          if (_gate) {
            copy += 1
          }
        }
        if (copy > 0) {
          hIn.copy(hOut, copy)
        }
        if (hIn.isDone) _gate = false
        if (!_gate) {
          gate = false
          if (hOut.flush()) completeStage()
        }

      } else {
        // XXX TODO --- should we advance hIn and hPred ? Probably not...
      }
    }
  }
}