/*
 *  WindowedLogic.scala
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

package de.sciss.fscape.stream.impl

import akka.stream.{Inlet, Shape}
import de.sciss.fscape.logStream
import de.sciss.fscape.stream.{BufD, BufElem, Node, StreamType}

import scala.annotation.tailrec

/** Ok, '''another''' (third) attempt to isolate a correct building block.
  * This is for window processing UGens where window parameters include
  * `winSize` and possibly others, and will be polled per window.
  */
trait WindowedLogic[/*@specialized(Int, Long, Double)*/ A, E >: Null <: BufElem[A], S <: Shape] extends Node {
  _: Handlers[S] =>

  // ---- abstract ----

  protected def aTpe  : StreamType[A, E]

  protected def hIn   : Handlers.InMain [A, E]
  protected def hOut  : Handlers.OutMain[A, E]

  /** Tries to prepare the parameters for the next window.
    * If successful, returns `true` otherwise `false`. If successful,
    * it must be possible to successively call `winBufSize`.
    */
  protected def tryObtainWinParams(): Boolean

  /** The size for the window buffer, or zero if this buffer should no be used.
    * This can be polled multiple times per window, the element might thus need to be saved
    * (in `tryObtainWinParams()`). In most cases, it will be sufficient to poll the value
    * in `tryObtainWinParams` and implement `winBufSize` by calling the `value` method of the
    * corresponding input handler.
    */
  protected def winBufSize: Int

  /** Called after a window has been fully read in. */
  protected def processWindow(): Unit

  // ---- default implementations that can be overridden if `super` is called ----

  protected def onDone(inlet: Inlet[_]): Unit =
    if (inlet == hIn.inlet) {
      if (stage == 0 || (stage == 1 && readOff == 0L)) {
        stage = 2
        if (hOut.flush()) completeStage()
      } else if (stage == 1) { // i.e. readOff > 0
        flushStage1Enter2()
        process()
      }
    }

  private def flushStage1Enter2(): Unit = {
    if (readRem > 0L && fullLastWindow) clearWindowTail()
    enterStage2()
  }

  private def enterStage2(): Unit = {
    processWindow()
    writeOff    = 0L
    writeRem    = writeWinSize
    stage       = 2
  }

  override protected def stopped(): Unit = {
    super.stopped()
    winBuf = null
  }

  // ---- default implementations that can be overridden ----

  protected val fullLastWindow: Boolean = true

  /** The default number of frames to read in per window equals the window buffer size */
  protected def readWinSize : Long = winBufSize

  /** The number of frames to write out per window. This is polled once after `processWindow`.
    * The default equals the window buffer size (`winBufSize`).
    * If an implementation wants to truncate the last window when the input terminates,
    * it should override `fullLastWindow` to return `false`, in which case the default
    * implementation of `writeWinSize` will return ``
    */
  protected def writeWinSize: Long = if (fullLastWindow) winBufSize else readOff

  /** Reads in a number of frames. The default implementation copies to the window buffer. */
  protected def readIntoWindow(n: Int): Unit = {
    val offI = readOff.toInt
    hIn.nextN(winBuf, offI, n)
  }

  /** Writes out a number of frames. The default implementation copies from the window buffer. */
  protected def writeFromWindow(n: Int): Unit = {
    val offI = writeOff.toInt
    hOut.nextN(winBuf, offI, n)
  }

  /** The default implementation clears from `readOff` to the end of the window buffer.
    * This method is not called if `fullLastWindow` returns `false`!
    */
  protected final def clearWindowTail(): Unit = {
    val _buf = winBuf
    if (_buf != null && _buf.length > readOff) {
      val offI = readOff.toInt
      aTpe.clear(winBuf, offI, _buf.length - offI)
    }
    readOff += readRem
    readRem  = 0L
  }

  // ---- visible impl ----

  protected final var winBuf: Array[A] = _
  protected final var readRem   = 0L
  protected final var readOff   = 0L
  protected final var writeOff  = 0L
  protected final var writeRem  = 0L

  // ---- impl ----

  private[this] var stage = 0 // 0: gather window parameters, 1: gather input, 2: produce output

  @tailrec
  final protected def process(): Unit = {
    logStream(s"process() $this")

    if (stage == 0) {
      if (!tryObtainWinParams()) return

      val _winBufSz = winBufSize
      if (winBuf == null || winBuf.length != _winBufSz) {
        winBuf = if (_winBufSz == 0) null else aTpe.newArray(_winBufSz)
      }

      readOff  = 0L
      readRem  = readWinSize
      stage     = 1
    }

    if (stage == 1) {
      while (stage == 1) {
        val remIn = hIn.available
        if (remIn == 0) return
        val numIn = math.min(remIn, readRem).toInt
        if (numIn > 0) readIntoWindow(numIn)
        readOff += numIn
        readRem -= numIn
        if (hIn.isDone) {
          flushStage1Enter2()
        } else if (readRem == 0) {
          enterStage2()
        }
      }
    }

    if (stage == 2) {
      while (stage == 2) {
        val remOut = hOut.available
        if (remOut == 0) return
        val numOut = math.min(remOut, writeRem).toInt
        if (numOut > 0) writeFromWindow(numOut)
        writeOff += numOut
        writeRem -= numOut
        if (writeRem == 0) {
          if (hIn.isDone) {
            if (hOut.flush()) {
              completeStage()
              return
            }
          } else {
            stage = 0
          }
        }
      }
    }

    process()
  }
}

/** Windowed logic for double I/O */
trait WindowedLogicD[S <: Shape] extends WindowedLogic[Double, BufD, S] {
  _: Handlers[S] =>

  protected final val aTpe: StreamType[Double, BufD] = StreamType.double
}