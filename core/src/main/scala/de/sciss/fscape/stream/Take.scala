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
  def head[A, E >: Null <: BufElem[A]](in: Outlet[E])
                                      (implicit b: Builder): Outlet[E] = {
    val length = ConstantL(1).toLong
    apply[A, E](in = in, length = length)
  }

  def apply[A, E >: Null <: BufElem[A]](in: Outlet[E], length: OutL)
                                       (implicit b: Builder): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(length, stage.in1)
    stage.out
  }

  private final val name = "Take"

  private type Shape[E] = FanInShape2[E, BufL, E]

  private final class Stage[A, E >: Null <: BufElem[A]](layer: Layer)(implicit ctrl: Control)
    extends StageImpl[Shape[E]](name) {

    val shape = new FanInShape2(
      in0 = Inlet [E](s"$name.in"    ),
      in1 = InL      (s"$name.length"),
      out = Outlet[E](s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[E], layer: Layer)
                                                       (implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with OutHandler { logic =>

    private[this] var takeRemain    = Long.MaxValue
    private[this] var hasLen       = false

    def onPull(): Unit = {
      val ok = hasLen && isAvailable(shape.in0)
      log(s"$this onPull() hasLen && isAvailable(in0) ? $ok")
      if (ok) {
        process()
      }
    }

    private object InH extends InHandler {
      override def toString: String = s"$logic.in"

      def onPush(): Unit = {
        val ok = hasLen && isAvailable(shape.out)
        log(s"$this onPush() hasLen && isAvailable(out) ? $ok")
        if (ok) {
          process()
        }
      }

      override def onUpstreamFinish(): Unit = {
        val cond = !isAvailable(shape.in0)
        log(s"$this onUpstreamFinish() !isAvailable(in0) ? $cond")
        if (cond) super.onUpstreamFinish()
      }
    }

    private object LenH extends InHandler {
      override def toString: String = s"$logic.length"

      def onPush(): Unit = {
        val buf = grab(shape.in1)
        val ok  = !hasLen
        log(s"$this onPush() !hasLen ? $ok")
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
        log(s"$this onUpstreamFinish() !hasLen ? $cond")
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

//      if ((takeRemain % 100000) == 0) println(s"takeRemain $takeRemain")

      if (takeRemain == 0L || isClosed(shape.in0)) completeStage()
      else pull(shape.in0)
    }
  }
}