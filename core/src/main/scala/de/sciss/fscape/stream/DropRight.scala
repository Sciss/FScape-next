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
import Handlers._

object DropRight {
  def apply[A, E >: Null <: BufElem[A]](in: Outlet[E], length: OutI)
                                       (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(length, stage.in1)
    stage.out
  }

  private final val name = "DropRight"

  private type Shp[E] = FanInShape2[E, BufI, E]

  private final class Stage[A, E >: Null <: BufElem[A]](layer: Layer)
                                                       (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape = new FanInShape2(
      in0 = Inlet [E](s"$name.in"     ),
      in1 = InI      (s"$name.length" ),
      out = Outlet[E](s"$name.out"    )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      new Logic[A, E](shape, layer)
//      val res: Logic[_, _] = if (tpe.isInt) {
//        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer)
//      } else if (tpe.isLong) {
//        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer)
//      } else if (tpe.isDouble) {
//        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer)
//      } else {
//        new Logic[A, E](shape, layer)
//      }
//
//      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Logic[/*@specialized(Int, Long, Double)*/ A, E >: Null <: BufElem[A]](shape: Shp[E], layer: Layer)
                                                       (implicit ctrl: Control, aTpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    private[this] val hIn     : InMain  [A, E]  = InMain [A, E](this, shape.in0)
    private[this] val hLen    : InIAux          = InIAux       (this, shape.in1)(math.max(0, _))
    private[this] val hOut    : OutMain [A, E]  = OutMain[A, E](this, shape.out)
    private[this] var needsLen  = true
    private[this] var buf: Array[A] = _   // circular
    private[this] var dropLen   = 0
    private[this] var bufLen    = 0
    private[this] var bufPosIn  = 0
    private[this] var bufPosOut = 0
    private[this] var advance   = 0   // in-pointer ahead of out-pointer

    protected def onDone(inlet: Inlet[_]): Unit = {
      assert (inlet == shape.in0)
      checkInDone()
    }

    private def checkInDone(): Boolean = {
      val res = advance <= dropLen && hOut.flush()
      if (res) completeStage()
      res
    }

    override protected def stopped(): Unit = {
      super.stopped()
      buf = null
    }

    protected def process(): Unit = {
      logStream(s"process() $this")

      if (needsLen) {
        if (!hLen.hasNext) return

        dropLen   = hLen.next()
        // because we always process in before out,
        // it is crucial that the buffer be _larger_ than the `dropLen`
        bufLen    = ctrl.blockSize + dropLen
        buf       = aTpe.newArray(bufLen)
        needsLen  = false
//        println(s"-- dropLen $dropLen, bufLen $bufLen")
      }

      // always enter here -- `needsLen` must be `false` now
      while (true) {
        val remIn   = hIn .available
        val remOut  = hOut.available

//        println(s"-- remIn $remIn, remOut $remOut, bufPosIn $bufPosIn, bufPosOut $bufPosOut, advance $advance")

        // never go beyond buffer end, or be ahead more than `bufLen` frames
        val numIn = math.min(remIn, bufLen - math.max(bufPosIn, advance))
        if (numIn > 0) {
          hIn .nextN(buf, bufPosIn, numIn)
          bufPosIn  = (bufPosIn + numIn) % bufLen
          advance  += numIn
        }
        // N.B. `numOut` can be negative if `advance < dropLen`
        val numOut = math.min(remOut, math.min(advance - dropLen, bufLen - bufPosOut))
        if (numOut > 0) {
          hOut.nextN(buf, bufPosOut, numOut)
          bufPosOut = (bufPosOut + numOut) % bufLen
          advance  -= numOut
        }

//        println(s"  -- numIn $numIn, numOut $numOut, bufPosIn $bufPosIn, bufPosOut $bufPosOut, advance $advance")

        // N.B. `numOut` can be negative if `advance < dropLen`
        if (numIn == 0 && numOut <= 0) return

        if (hIn.isDone) {
//          println(s"(hIn.isDone) ; advance $advance")
          if (checkInDone()) return
        }
      }
    }
  }
}