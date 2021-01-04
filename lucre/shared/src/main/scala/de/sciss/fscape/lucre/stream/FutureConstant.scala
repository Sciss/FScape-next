/*
 *  FutureConstant.scala
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

package de.sciss.fscape.lucre
package stream

import akka.stream.stage.OutHandler
import akka.stream.{Attributes, Outlet, SourceShape}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.fscape.stream.{BufElem, Builder, Control, Layer, StreamType}

import scala.concurrent.Future

object FutureConstant {
  def apply[A, E <: BufElem[A]](fut: Control => Future[A])(implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer, fut)
    val stage   = b.add(stage0)
    stage.out
  }

  private final val name = "FutureConstant"

  private type Shp[E] = SourceShape[E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer, fut: Control => Future[A])
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = SourceShape(
      Outlet[E](s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer, fut)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer, fut: Control => Future[A])
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends NodeImpl[Shp[E]](name, layer, shape) with OutHandler {

    private[this] var result: E = _
    private[this] var _stopped = false

    override def toString: String = name

    override protected def launch(): Unit = {
      super.launch()
      val futV = fut(ctrl)
      import ctrl.config.executionContext
      futV.foreach { v =>
        async {
          if (!_stopped) {
            val b = tpe.allocBuf()
            b.buf(0)  = v
            b.size    = 1
            result    = b
            if (isAvailable(shape.out)) onPull()
          }
        }
      }
    }

    override protected def stopped(): Unit = {
      super.stopped()
      _stopped = true
    }

    def onPull(): Unit = {
      if (result != null) {
        push(shape.out, result)
        completeStage()
      }
    }
    setHandler(shape.out, this)
  }
}
