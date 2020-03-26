/*
 *  DropRight.scala
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

object DropRight {
  def apply[A, Buf >: Null <: BufElem[A]](in: Outlet[Buf], length: OutI)
                                         (implicit b: Builder, aTpe: StreamType[A, Buf]): Outlet[Buf] = {
    val stage0  = new Stage[A, Buf](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(length, stage.in1)
    stage.out
  }

  private final val name = "DropRight"

  private type Shape[A, Buf >: Null <: BufElem[A]] = FanInShape2[Buf, BufI, Buf]

  private final class Stage[A, Buf >: Null <: BufElem[A]](layer: Layer)
                                                         (implicit ctrl: Control, aTpe: StreamType[A, Buf])
    extends StageImpl[Shape[A, Buf]](name) {

    val shape = new FanInShape2(
      in0 = Inlet [Buf](s"$name.in"     ),
      in1 = InI        (s"$name.length" ),
      out = Outlet[Buf](s"$name.out"    )
    )

    def createLogic(attr: Attributes): NodeImpl[DropRight.Shape[A, Buf]] = new Logic(shape, layer)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[A, E], layer: Layer)
                                                       (implicit ctrl: Control, aTpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    private[this] val hIn       = new Handlers.InMain [A, E](this, shape.in0)()
    private[this] val hLen      = new Handlers.InIAux       (this, shape.in1)(math.max(0, _))
    private[this] val hOut      = new Handlers.OutMain[A, E](this, shape.out)
    private[this] var needsLen  = true
    private[this] var buf: Array[A] = _   // circular
    private[this] var dropLen   = 0
    private[this] var bufLen    = 0
    private[this] var bufPosIn  = 0
    private[this] var bufPosOut = 0
    private[this] var initRem   = 0   // initial "free" space
    private[this] var advance   = 0   // in-pointer ahead of out-pointer

    protected def onDone(inlet: Inlet[_]): Unit = {
      assert (inlet == shape.in0)
      checkInDone()
    }

    private def checkInDone(): Boolean = {
      val res = advance == dropLen && hOut.flush()
      if (res) completeStage()
      res
    }

    def process(): Unit = {
      logStream(s"process() $this")

      if (needsLen) {
        if (!hLen.hasNext) return

        dropLen   = hLen.next()
        bufLen    = math.max(256 /*ctrl.blockSize*/, dropLen)
        buf       = aTpe.newArray(bufLen)
        initRem   = dropLen
        needsLen  = false
      }

      {
        // always enter here -- `needsLen` must be `false` now
        while (true) {
          if (initRem > 0) {
            val remIn = hIn.available
            if (remIn == 0) return
            val numIn = math.min(initRem, remIn)
            hIn.nextN(buf, bufPosIn, numIn)
            bufPosIn  = (bufPosIn + numIn) % bufLen
            initRem  -= numIn
            advance  += numIn

          } else {
            val remIn   = hIn .available
            val remOut  = hOut.available
            // never go beyond buffer end, or be ahead more than `bufLen` frames
            val numIn = math.min(remIn, bufLen - math.max(bufPosIn, advance))
            if (numIn > 0) {
              hIn .nextN(buf, bufPosIn, numIn)
              bufPosIn  = (bufPosIn + numIn) % bufLen
              advance  += numIn
            }
            val numOut = math.min(remOut, math.min(advance - dropLen, bufLen - bufPosOut))
            if (numOut > 0) {
              hOut.nextN(buf, bufPosOut, numOut)
              bufPosOut = (bufPosOut + numOut) % bufLen
              advance  -= numOut
            }
            if (numIn == 0 && numOut == 0) return
          }
          if (hIn.isDone) {
            if (checkInDone()) return
          }
        }
      }
    }
  }
}