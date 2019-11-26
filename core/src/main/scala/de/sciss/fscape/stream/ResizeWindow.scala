/*
 *  ResizeWindow.scala
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

import akka.stream.{Attributes, FanInShape4, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{DemandFilterWindowedLogic, NodeImpl, StageImpl}

object ResizeWindow {
  /** Resizes the windowed input signal by trimming each
    * windows boundaries (if `start` is greater than zero
    * or `stop` is less than zero) or padding the boundaries
    * with zeroes (if `start` is less than zero or `stop` is
    * greater than zero). The output window size is thus
    * `size - start + stop`.
    *
    * @param in     the signal to window and resize
    * @param size   the input window size
    * @param start  the delta window size at the output window's beginning
    * @param stop   the delta window size at the output window's ending
    */
  def apply[A, E >: Null <: BufElem[A]](in: Outlet[E], size: OutI, start: OutI, stop: OutI)
                                       (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(size  , stage.in1)
    b.connect(start , stage.in2)
    b.connect(stop  , stage.in3)

    stage.out
  }

  private final val name = "ResizeWindow"

  private type Shape[E] = FanInShape4[E, BufI, BufI, BufI, E]

  private final class Stage[A, E >: Null <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shape[E]](name) {

    val shape = new FanInShape4(
      in0 = Inlet[E]  (s"$name.in"   ),
      in1 = InI       (s"$name.size" ),
      in2 = InI       (s"$name.start"),
      in3 = InI       (s"$name.stop" ),
      out = Outlet[E] (s"$name.out"  )
    )

    def createLogic(attr: Attributes) = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[E], layer: Layer)
                                                       (implicit ctrl: Control,
                                                        protected val tpeSignal: StreamType[A, E])
    extends NodeImpl(name, layer, shape)
      with DemandFilterWindowedLogic[A, E, Shape[E]] {

    private[this] var startPos    : Int = -1
    private[this] var startNeg    : Int = _
    private[this] var stopPos     : Int = -1
    private[this] var stopNeg     : Int = _

    private[this] var bufStartOff : Int   = 0
    private[this] var bufStart    : BufI  = _
    private[this] var bufStopOff  : Int   = 0
    private[this] var bufStop     : BufI  = _

    private[this] var needsStart  = true
    private[this] var needsStop   = true

    private def startValid  = startPos  >= 0
    private def stopValid   = stopPos   >= 0

    // constructor
    {
      installMainAndWindowHandlers()
      new _InHandlerImpl(inletStart)(startValid)
      new _InHandlerImpl(inletStop )(stopValid )
    }

    protected def inletSignal : Inlet[E]  = shape.in0
    protected def inletWinSize: InI       = shape.in1
    private   def inletStart  : InI       = shape.in2
    private   def inletStop   : InI       = shape.in3

    protected def out0        : Outlet[E] = shape.out

    protected def winParamsValid: Boolean = startValid && stopValid
    protected def needsWinParams: Boolean = needsStart | needsStop

    protected def requestWinParams(): Unit = {
      needsStart  = true
      needsStop   = true
    }

    protected def freeWinParamBuffers(): Unit = {
      freeStartBuf()
      freeStopBuf ()
    }

    private def freeStartBuf(): Unit =
      if (bufStart != null) {
        bufStart.release()
        bufStart = null
      }

    private def freeStopBuf(): Unit =
      if (bufStop != null) {
        bufStop.release()
        bufStop = null
      }

    protected def tryObtainWinParams(): Boolean = {
      var stateChange = false

      if (needsStart && bufStart != null && bufStartOff < bufStart.size) {
        val start   = bufStart.buf(bufStartOff)
        startPos    = math.max(0, start)
        startNeg    = math.min(0, start)
        bufStartOff += 1
        needsStart  = false
        stateChange = true
      } else if (isAvailable(inletStart)) {
        freeStartBuf()
        bufStart    = grab(inletStart)
        bufStartOff = 0
        tryPull(inletStart)
        stateChange = true
      } else if (needsStart && isClosed(inletStart) && startValid) {
        needsStart  = false
        stateChange = true
      }

      if (needsStop && bufStop != null && bufStopOff < bufStop.size) {
        val stop    = bufStop.buf(bufStopOff)
        stopPos     = math.max(0, stop)
        stopNeg     = math.min(0, stop)
        bufStopOff += 1
        needsStop   = false
        stateChange = true
      } else if (isAvailable(inletStop)) {
        freeStopBuf()
        bufStop     = grab(inletStop)
        bufStopOff  = 0
        tryPull(inletStop)
        stateChange = true
      } else if (needsStop && isClosed(inletStop) && stopValid) {
        needsStop   = false
        stateChange = true
      }

      stateChange
    }

    override protected def allWinParamsReady(winInSize: Int): Int =
      math.max(0 /*1*/, winInSize - startPos + stopNeg)

    override protected def prepareWindow(win: Array[A], winInSize: Int, inSignalDone: Boolean): Long =
      if (inSignalDone && winInSize == 0) 0
      else math.max(0 /*1*/, winInSize - (startPos + startNeg) + (stopPos + stopNeg))

    override protected def clearInputTail(win: Array[A], readOff: Layer, chunk: Layer): Unit = {
      val writeOffI = readOff
      val skipStart = math.max(0, startPos - writeOffI)
      if (skipStart > chunk) return

      val winOff1   = writeOffI + skipStart - startPos
      val chunk1    = /*if (win == null) 0 else*/ math.min(chunk - skipStart, win.length - winOff1)
      if (chunk1 <= 0) return

      tpeSignal.clear(win, winOff1, chunk1)
    }

    override protected def processInput(in: Array[A], inOff: Int, win: Array[A], readOff: Int, chunk: Int): Unit = {
      val writeOffI = readOff // writeToWinOff.toInt
      // ex. startPos = 10, writeToWinOff = 4, chunk = 12, inOff = 7
      // then skipStart becomes 6, inOff1 becomes 13, winOff1 becomes 4 + 6 - 10 = 0, chunk1 becomes 6
      // and we effectively begin writing to the buffer begin having skipped 10 input frames.
      val skipStart = math.max(0, startPos - writeOffI)
      if (skipStart > chunk) return

      val inOff1    = inOff + skipStart
      val winOff1   = writeOffI + skipStart - startPos
      val chunk1    = /*if (win == null) 0 else*/ math.min(chunk - skipStart, win.length - winOff1)
      if (chunk1 <= 0) return

      System.arraycopy(in, inOff1, win, winOff1, chunk1)
    }

    override protected def processOutput(win: Array[A], winInSize : Int , writeOff: Long,
                                         out: Array[A], winOutSize: Long, outOff  : Int, chunk: Int): Unit = {
      val readOffI  = writeOff.toInt
      val arr       = bufOut0.buf
      val zeroStart = math.min(chunk, math.max(0, -startNeg - readOffI))
      if (zeroStart > 0) {
        tpeSignal.clear(arr, outOff, zeroStart)
      }
      val winOff1   = readOffI + zeroStart + startNeg
      val outOff1   = outOff + zeroStart
      val chunk1    = chunk - zeroStart
      val chunk2    = /*if (win == null) 0 else*/ math.min(chunk1, math.max(0, win.length - winOff1))
      if (chunk2 > 0) {
        System.arraycopy(win, winOff1, arr, outOff1, chunk2)
      }

      val zeroStop  = chunk - (chunk2 + zeroStart) // - chunk1
      if (zeroStop > 0) {
        val outOff2 = outOff1 + chunk2
        tpeSignal.clear(arr, outOff2, zeroStop)
      }
    }
  }
}