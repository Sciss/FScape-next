/*
 *  MatchLen.scala
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

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object MatchLen {
  def apply[A, E <: BufElem[A]](in: Outlet[E], ref: OutA)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in , stage.in0)
    b.connect(ref, stage.in1)
    stage.out
  }

  private final val name = "MatchLen"

  private type Shp[E] = FanInShape2[E, BufLike, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet [E] (s"$name.in"  ),
      in1 = InA       (s"$name.ref" ),
      out = Outlet[E] (s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends NodeImpl(name, layer, shape) with OutHandler with InHandler { logic =>

    private[this] var refDone = false
    private[this] var isZero  = false
    private[this] var refLen  = 0L
    private[this] var inLen   = 0L

    private object RefH extends InHandler {
      def onPush(): Unit = {
        val refBuf = grab(shape.in1)
        refLen += refBuf.size
        refBuf.release()
        tryPull(shape.in1)
      }

      override def onUpstreamFinish(): Unit = {
        if (isAvailable(shape.in1)) {
          onPush()
        }
        refDone = true
        process()
      }
    }

    private[this] var inBuf: E = _

    private def writeInBuf(): Unit = {
      if (inBuf.size > 0) {
        push(shape.out, inBuf)
        inLen += inBuf.size
      } else {
        inBuf.release()
      }
      inBuf = null.asInstanceOf[E]
    }

    private def process(): Unit = {
      if (inBuf == null && isAvailable(shape.in0)) {
        inBuf = grab(shape.in0)
        tryPull(shape.in0)
      }

      if (inBuf != null) {
        if (isAvailable(shape.out)) {
          if (inLen + inBuf.size <= refLen) {
            writeInBuf()
          } else if (refDone) {
            inBuf.size = (refLen - inLen).toInt
            writeInBuf()
          }
        }
      } else if (isZero) {
        if (isAvailable(shape.out) && (inLen < refLen)) {
          val zeroBuf = tpe.allocBuf()
          tpe.clear(zeroBuf.buf, 0, zeroBuf.size)
          zeroBuf.size = math.min(zeroBuf.size, (refLen - inLen).toInt)
          push(shape.out, zeroBuf)
          inLen += zeroBuf.size
        }
      }

      if (refDone && inLen == refLen) completeStage()
    }

    def onPush(): Unit =
      process()

    def onPull(): Unit =
      process()

    override def onUpstreamFinish(): Unit = {
      process()
      isZero = true
      process()
    }

    setHandler(shape.in0, this)
    setHandler(shape.in1, RefH)
    setHandler(shape.out, this)
  }
}