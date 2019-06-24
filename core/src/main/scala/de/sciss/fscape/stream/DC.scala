/*
 *  DC.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
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
  def apply[A, Buf >: Null <: BufElem[A]](in: Outlet[Buf])
                                         (implicit b: Builder, aTpe: StreamType[A, Buf]): Outlet[Buf] = {
    val stage0  = new Stage[A, Buf](b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "DC"

  private type Shape[A, Buf >: Null <: BufElem[A]] = FlowShape[Buf, Buf]

  private final class Stage[A, Buf >: Null <: BufElem[A]](layer: Layer)
                                                         (implicit ctrl: Control, aTpe: StreamType[A, Buf])
    extends StageImpl[Shape[A, Buf]](name) {

    val shape = new FlowShape(
      in  = Inlet [Buf](s"$name.in" ),
      out = Outlet[Buf](s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic[A, Buf >: Null <: BufElem[A]](shape: Shape[A, Buf], layer: Layer)
                                                         (implicit ctrl: Control, aTpe: StreamType[A, Buf])
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
      val buf = aTpe.allocBuf()
      aTpe.fill(buf.buf, 0, buf.size, value)
      push(shape.out, buf)
    }
  }
}