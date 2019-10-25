/*
 *  RotateWindow.scala
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

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{DemandFilterWindowedLogic, NodeImpl, StageImpl}
import de.sciss.numbers.IntFunctions

object RotateWindow {
  def apply[A, E >: Null <: BufElem[A]](in: Outlet[E], size: OutI, amount: OutI)
                                       (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(size  , stage.in1)
    b.connect(amount, stage.in2)

    stage.out
  }

  private final val name = "RotateWindow"

  private type Shape[E] = FanInShape3[E, BufI, BufI, E]

  private final class Stage[A, E >: Null <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shape[E]](name) {
    
    val shape = new FanInShape3(
      in0 = Inlet[E]  (s"$name.in"    ),
      in1 = InI       (s"$name.size"  ),
      in2 = InI       (s"$name.amount"),
      out = Outlet[E] (s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[E], layer: Layer)
                                                       (implicit ctrl: Control,
                                                        protected val tpeSignal: StreamType[A, E])
    extends NodeImpl(name, layer, shape)
      with DemandFilterWindowedLogic[A, E, Shape[E]] {

    private[this] var amount         : Int  = _
    private[this] var amountInv      : Int  = -1
    private[this] var bufAmountOff   : Int  = 0
    private[this] var bufAmount      : BufI = _
    private[this] var needsAmount           = true

    private def amountValid = amountInv >= 0

    // constructor
    {
      installMainAndWindowHandlers()
      new _InHandlerImpl(inletAmount)(amountValid)
    }

    protected def inletSignal : Inlet[E]  = shape.in0
    protected def inletWinSize: InI       = shape.in1
    private   def inletAmount : InI       = shape.in2

    protected def out0        : Outlet[E] = shape.out

    protected def winParamsValid: Boolean = amountValid
    protected def needsWinParams: Boolean = needsAmount

    protected def requestWinParams(): Unit = {
      needsAmount = true
    }

    protected def freeWinParamBuffers(): Unit =
      freeAmountBuf()

    private def freeAmountBuf(): Unit =
      if (bufAmount != null) {
        bufAmount.release()
        bufAmount = null
      }

    protected def tryObtainWinParams(): Boolean =
      if (needsAmount && bufAmount != null && bufAmountOff < bufAmount.size) {
        amount       = bufAmount.buf(bufAmountOff)
//        val amount  = IntFunctions.mod(bufAmount.buf(bufAmountOff), winSize)
        bufAmountOff   += 1
        needsAmount = false
        true
      } else if (isAvailable(inletAmount)) {
        freeAmountBuf()
        bufAmount    = grab(inletAmount)
        bufAmountOff = 0
        tryPull(inletAmount)
        true
      } else if (needsAmount && isClosed(inletAmount) && amountValid) {
        needsAmount = false
        true
      } else {
        false
      }

    override protected def allWinParamsReady(winInSize: Layer): Layer = {
      val amountM = IntFunctions.mod(amount, winInSize)
      amountInv   = winInSize - amountM
      winInSize
    }

    override protected def processOutput(win: Array[A], winInSize : Int , writeOff: Long,
                                         out: Array[A], winOutSize: Long, outOff  : Int, chunk: Int): Unit = {
      val n         = (writeOff.toInt + amountInv) % winInSize
      val m         = math.min(chunk, winInSize - n)
      System.arraycopy(win, n, out, outOff, m)
      val p         = chunk - m
      if (p > 0) {
        System.arraycopy(win, 0, out, outOff + m, p)
      }
    }
  }
}