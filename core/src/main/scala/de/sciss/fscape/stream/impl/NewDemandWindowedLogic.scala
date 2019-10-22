/*
 *  NewDemandWindowedLogic.scala
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

package de.sciss.fscape.stream.impl

import akka.stream.stage.{GraphStageLogic, InHandler}
import akka.stream.{Inlet, Outlet, Shape}
import de.sciss.fscape.stream.{BufElem, BufI, InI}
import de.sciss.fscape.{logStream, stream}

import scala.annotation.tailrec

/** Ok, '''another''' attempt to isolate a correct building block.
  * This is for window processing UGens where window parameters include
  * `winSize` and possibly others, and will be polled per window.
  * Input windows are buffered, output windows can be arbitrary in size
  * (`Long`).
  *
  * Implementations should call `installMainAndWindowHandlers()` in their
  * constructor, and add handlers for all other inlets.
  */
trait NewDemandWindowedLogic[A, E >: Null <: BufElem[A], S <: Shape]
  extends Out1LogicImpl[E, S] {

    _: GraphStageLogic =>

  // ---- abstract ----

  protected def tpeSignal: stream.StreamType[A, E]

  protected def winParamsValid: Boolean

  protected def inletSignal   : Inlet [E]
  protected def inletWinSize  : InI
  protected def out0          : Outlet[E]

  protected def freeWinParamBuffers(): Unit

  protected def tryObtainWinParams(): Boolean

  /** Called one or several times per window, when the output buffer
    * should be filled.
    *
    * @param  win        the input window array
    * @param  winInSize the valid size in `in`
    * @param  writeOff  the number of output frames processed so far.
    *                   This is an accumulation of `chunk` across multiple invocations per window.
    * @param  out       the output window array to fill by this method.
    * @param  winOutSize  the valid size in `out` (as previously reported through `winInDoneCalcWinOutSize`)
    * @param  outOff    the offset in `out` from which on it should be filled
    * @param  chunk     the number of values to fill in `out`
    */
  protected def processOutput(win : Array[A], winInSize : Int, writeOff: Long,
                              out : Array[A], winOutSize: Long, outOff: Int, chunk: Int): Unit

  protected def needsWinParams: Boolean

  protected def requestWinParams(): Unit

  // ---- possible to override ----

  /** Called when all new window parameters have been obtained.
    * Returns the buffer size for the internal `win` array.
    *
    * The default implementation returns `winInSize`.
    */
  protected def allWinParamsReady(winInSize: Int): Int =
    winInSize

  /** Called when the input window has been fully read.
    * The implementation may update the `in` array if needed,
    * or perform additional initializations. It should then
    * return the write-size (`winOutSize`).
    *
    * The default implementation returns `winInSize`.
    */
  protected def winInDoneCalcWinOutSize(win: Array[A], winInSize: Int): Long =
    winInSize

  /** The default implementation zeroes the window buffer. */
  protected def clearInputTail(win: Array[A], readOff: Int, chunk: Int): Unit =
    tpeSignal.clear(win, readOff, chunk)

  /** The default implementation copies the input to the window. */
  protected def processInput(in: Array[A], inOff: Int, win: Array[A], readOff: Int, chunk: Int): Unit =
    System.arraycopy(in, inOff, win, readOff, chunk)

  override protected def stopped(): Unit = {
    super.stopped()
    freeInputBuffers()
    freeOutputBuffers()
    winBuf = null
  }

  // ---- impl ----

  private[this] var winBuf : Array[A] = _

  private[this] var inSignalRemain: Int = 0

  private[this] var winInSize : Int   = -1
  private[this] var readOff   : Int   = 0
  private[this] var writeOff  : Long  = 0
  private[this] var writeSize : Long  = 0

  private[this] var needsWinSize  = true

  private[this] var inSignalOff : Int   = 0
  private[this] var inWinSizeOff: Int   = 0
  private[this] var outOff0     : Int   = 0

  private[this] var stage = 0 // 0: gather window parameters, 1: gather input, 2: produce output
  private[this] var inSignalDone = false

  private[this] var bufInSignal : E  = _

  private[this] var bufInWinSize : BufI = _

  protected final var bufOut0: E  = _

  protected final def allocOutBuf0(): E = tpeSignal.allocBuf()

  protected final class _InHandlerImpl[B](in: Inlet[B])(isValid: => Boolean) extends InHandler {
    def onPush(): Unit = {
      logStream(s"onPush($in)")
      process()
    }

    override def onUpstreamFinish(): Unit = {
      logStream(s"onUpstreamFinish($in)")
      if (isValid) {
        process()
      } else if (!isInAvailable(in)) {
        super.onUpstreamFinish()
      }
    }

    setInHandler(in, this)
  }

  protected final def installMainAndWindowHandlers(): Unit = {
    new _InHandlerImpl(inletSignal)(true)
    new _InHandlerImpl(inletWinSize)(winInSize >= 0)
    new ProcessOutHandlerImpl(out0, this)
  }

  final def inValid: Boolean = winInSize >= 0 && winParamsValid

  private def freeBufInSignal(): Unit =
    if (bufInSignal != null) {
      bufInSignal.release()
      bufInSignal = null
    }

  private def freeBufInWinSize(): Unit =
    if (bufInWinSize != null) {
      bufInWinSize.release()
      bufInWinSize = null
    }

  private def freeInputBuffers(): Unit = {
    freeBufInSignal()
    freeBufInWinSize()
    freeWinParamBuffers()
  }

  protected def freeOutputBuffers(): Unit =
    if (bufOut0 != null) {
      bufOut0.release()
      bufOut0 = null
    }

  @tailrec
  final def process(): Unit = {
    var stateChange = false

    if (stage == 0) {
      if (needsWinSize) {
        if (bufInWinSize != null && inWinSizeOff < bufInWinSize.size) {
          // XXX TODO see if we can support `winSize == 0`
          winInSize = math.max(1, bufInWinSize.buf(inWinSizeOff))
          // println(s"winInSize = $winInSize")
          inWinSizeOff += 1
          needsWinSize  = false
          stateChange   = true
        } else if (isAvailable(inletWinSize)) {
          freeBufInWinSize()
          bufInWinSize  = grab(inletWinSize)
          inWinSizeOff  = 0
          tryPull(inletWinSize)
          stateChange = true
        } else if (isClosed(inletWinSize) && winInSize >= 0) {
          needsWinSize  = false
          stateChange   = true
        }
      }

      if (needsWinParams) {
        stateChange ||= tryObtainWinParams()
      }

      if (!needsWinSize && !needsWinParams) {
        readOff     = 0
        stage       = 1
        val winBufSz = allWinParamsReady(winInSize)
        // println(s"winBufSz = $winBufSz")
        if (winBuf == null || winBuf.length != winBufSz) {
          winBuf = tpeSignal.newArray(winBufSz)
        }
        stateChange = true
      }
    }

    if (stage == 1) {
      if (readOff < winInSize) {
        if (bufInSignal != null && inSignalRemain > 0) {
          val chunk = math.min(winInSize - readOff, inSignalRemain)
          processInput(in = bufInSignal.buf, inOff = inSignalOff, win = winBuf, readOff = readOff, chunk = chunk)
          inSignalOff     += chunk
          inSignalRemain  -= chunk
          readOff         += chunk
          stateChange      = true
        } else if (isAvailable(inletSignal)) {
          freeBufInSignal()
          bufInSignal     = grab(inletSignal)
          inSignalOff     = 0
          inSignalRemain  = bufInSignal.size
          tryPull(inletSignal)
          stateChange = true
        } else if (isClosed(inletSignal)) {
          // println(s"closed; readOff = $readOff")
          if (readOff > 0) {
            val chunk = winInSize - readOff
            clearInputTail(winBuf, readOff = readOff, chunk = chunk)
            readOff   = winInSize
          } else {
            winInSize   = 0
          }
          inSignalDone  = true
          stateChange   = true
        }
      }

      if (readOff == winInSize) {
        writeOff    = 0
        stage       = 2
        writeSize   = winInDoneCalcWinOutSize(winBuf, winInSize)
        // println(s"winInDoneCalcWinOutSize(_, $winInSize) = $writeSize")
        stateChange = true
      }
    }

    if (stage == 2) {
      if (bufOut0 == null) {
        bufOut0 = allocOutBuf0()
        outOff0 = 0
      }

      if (writeOff < writeSize) {
        if (outOff0 < bufOut0.size) {
          val chunk = math.min(writeSize - writeOff, bufOut0.size - outOff0).toInt
          // if (winSize == 0) println(s"Fuckedifuck! $chunk")
          processOutput(
            win = winBuf      , winInSize   = winInSize , writeOff  = writeOff,
            out = bufOut0.buf , winOutSize  = writeSize , outOff    = outOff0,
            chunk = chunk
          )
          writeOff   += chunk
          outOff0    += chunk
          stateChange = true
        }
      }

      if (outOff0 == bufOut0.size && canWrite) {
        writeOuts(outOff0)
        stateChange = true
      }

      if (writeOff == writeSize) {
        if (inSignalDone) {
          if (isAvailable(out0)) {
            writeOuts(outOff0)
            completeStage()
          }
        }
        else {
          stage         = 0
          needsWinSize  = true
          requestWinParams() // needsWinParams= true
          stateChange   = true
        }
      }
    }

    if (stateChange) process()
  }
}