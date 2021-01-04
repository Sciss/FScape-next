/*
 *  Frames.scala
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

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec

object Frames {
  def apply[A, E <: BufElem[A]](in: Outlet[E], init: Int = 1, name: String = nameFr)
                               (implicit b: Builder, tpe: StreamType[A, E]): OutL = {
    val stage0  = new Stage(b.layer, init = init, name = name)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val nameFr = "Frames"

  private type Shp[E] = FlowShape[E, BufL]

  private final class Stage[A, E <: BufElem[A]](layer: Layer, init: Int, name: String)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FlowShape(
      in  = Inlet[E](s"$name.in" ),
      out = OutL    (s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer, init = init, name = name)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer, init: Int, name: String)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    private[this] val hIn   = Handlers.InMain[A, E](this, shape.in)
    private[this] val hOut  = Handlers.OutLMain    (this, shape.out)

    private[this] var framesRead = init.toLong

    protected def onDone(inlet: Inlet[_]): Unit =
      if (hOut.flush()) completeStage()

    @tailrec
    protected def process(): Unit = {
      val num = math.min(hIn.available, hOut.available)
      if (num == 0) return

      val arr = hOut.array
      var i   = hOut.offset
      var j   = framesRead
      val stop = i + num
      while (i < stop) {
        arr(i) = j
        j += 1
        i += 1
      }
      hIn .skip   (num)
      hOut.advance(num)
      framesRead = j

      if (hIn.isDone) {
        if (hOut.flush()) completeStage()
        return
      }

      process()
    }
  }
}