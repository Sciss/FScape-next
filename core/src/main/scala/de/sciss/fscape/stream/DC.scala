/*
 *  DC.scala
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

package de.sciss.fscape.stream

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.fscape.{logStream => log}

object DC {
  def apply[A, Buf <: BufElem[A]](in: Outlet[Buf])
                                         (implicit b: Builder, tpe: StreamType[A, Buf]): Outlet[Buf] = {
    val stage0  = new Stage[A, Buf](b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "DC"

  private type Shp[E] = FlowShape[E, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FlowShape(
      in  = Inlet [E](s"$name.in" ),
      out = Outlet[E](s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isDouble) {
        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer)
      } else if (tpe.isInt) {
        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer)
      } else {
        assert (tpe.isLong)
        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer)
      }
      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Logic[@specialized(Args) A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends NodeImpl(name, layer, shape) with OutHandler { logic =>

    private[this] var hasValue = false
    private[this] var value: A = _

    def onPull(): Unit = {
      val ok = hasValue
      log(s"$this onPull() $ok")
      if (ok) {
        process()
      }
    }

    private object InH extends InHandler {
      override def toString: String = s"$logic.in"

      def onPush(): Unit = {
        val buf = grab(shape.in)
        val ok  = !hasValue
        log(s"$this onPush() $ok")
        if (ok) {
          value     = buf.buf(0)
          hasValue  = true
          if (isAvailable(shape.out)) process()
        }
        buf.release()
        tryPull(shape.in)
      }

      override def onUpstreamFinish(): Unit =
        if (!hasValue) super.onUpstreamFinish()
    }

    setHandler(shape.out, this)
    setHandler(shape.in, InH)

    private def process(): Unit = {
      val buf = tpe.allocBuf()
      tpe.fill(buf.buf, 0, buf.size, value)
      push(shape.out, buf)
    }
  }
}