/*
 *  Take.scala
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
import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.graph.ConstantL
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.fscape.{logStream => log}

object Take {
  def head[A, Buf >: Null <: BufElem[A]](in: Outlet[Buf])
                                        (implicit b: Builder): Outlet[Buf] = {
    val length = ConstantL(1).toLong
    apply[A, Buf](in = in, length = length)
  }

  def apply[A, Buf >: Null <: BufElem[A]](in: Outlet[Buf], length: OutL)
                                         (implicit b: Builder): Outlet[Buf] = {
    val stage0  = new Stage[A, Buf](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(length, stage.in1)
    stage.out
  }

  private final val name = "Take"

  private type Shape[A, Buf >: Null <: BufElem[A]] = FanInShape2[Buf, BufL, Buf]

  private final class Stage[A, Buf >: Null <: BufElem[A]](layer: Layer)
                                                         (implicit ctrl: Control)
    extends StageImpl[Shape[A, Buf]](name) {

    val shape = new FanInShape2(
      in0 = Inlet [Buf](s"$name.in"    ),
      in1 = InL        (s"$name.length"),
      out = Outlet[Buf](s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic[A, Buf >: Null <: BufElem[A]](shape: Shape[A, Buf], layer: Layer)
                                                         (implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with OutHandler { logic =>

    private[this] var takeRemain    = Long.MaxValue
    private[this] var hasLen       = false

    def onPull(): Unit = {
      val ok = hasLen && isAvailable(shape.in0)
      log(s"$this onPull() $ok")
      if (ok) {
        process()
      }
    }

    private object InH extends InHandler {
      override def toString: String = s"$logic.in"

      def onPush(): Unit = {
        val ok = hasLen && isAvailable(shape.out)
        log(s"$this onPush() $ok")
        if (ok) {
          process()
        }
      }

      override def onUpstreamFinish(): Unit = {
        val cond = !isAvailable(shape.in0)
        log(s"$this onUpstreamFinish() $cond")
        if (cond) super.onUpstreamFinish()
      }
    }

    private object LenH extends InHandler {
      override def toString: String = s"$logic.length"

      def onPush(): Unit = {
        val buf = grab(shape.in1)
        val ok  = !hasLen
        log(s"$this onPush() $ok")
        if (ok) {
          takeRemain  = math.max(0L, buf.buf(0))
          hasLen      = true
          if (takeRemain == 0L) completeStage()
          else {
            if (isAvailable(shape.in0) && isAvailable(shape.out)) process()
          }
        }
        buf.release()
        tryPull(shape.in1)
      }

      override def onUpstreamFinish(): Unit = {
        val cond = !hasLen
        log(s"$this onUpstreamFinish() $cond")
        if (cond) super.onUpstreamFinish()
      }
    }

    setHandler(shape.out, this)
    setHandler(shape.in0, InH)
    setHandler(shape.in1, LenH)

    private def process(): Unit = {
      val buf   = grab(shape.in0)
      val chunk = math.min(takeRemain, buf.size).toInt
      if (chunk > 0) {
        buf.size = chunk
        push(shape.out, buf)
        takeRemain -= chunk
      } else {
        buf.release()
      }

      if (takeRemain == 0L || isClosed(shape.in0)) completeStage()
      else pull(shape.in0)
    }
  }
}