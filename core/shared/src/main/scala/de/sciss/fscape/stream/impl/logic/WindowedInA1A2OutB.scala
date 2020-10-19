/*
 *  WindowedInA1A2OutB.scala
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

package de.sciss.fscape.stream
package impl.logic

import akka.stream.Inlet
import de.sciss.fscape.logStream
import de.sciss.fscape.stream.impl.Handlers

import scala.annotation.tailrec
import scala.math.min

/** This is a building block for window processing UGens with two main inputs,
  *  where window parameters include
  * `winSize` and possibly others, and will be polled per window.
  */
trait WindowedInA1A2OutB[A1, E1 <: BufElem[A1], A2, E2 <: BufElem[A2], B, F <: BufElem[B], C]
  extends Node {

  _: Handlers[_] =>

  // ---- abstract ----

  /** The first input signal type */
  protected def a1Tpe  : StreamType[A1, E1]
  /** The second input signal type */
  protected def a2Tpe  : StreamType[A2, E2]
  /** The output signal type */
  protected def bTpe  : StreamType[B, F]

  protected def hIn1  : Handlers.InMain [A1, E1]
  protected def hIn2  : Handlers.InMain [A2, E2]
  protected def hOut  : Handlers.OutMain[B, F]

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

  /** Reads in a number of frames. */
  protected def readIntoWindow(n: Int): Unit

  /** Writes out a number of frames. */
  protected def writeFromWindow(n: Int): Unit

  protected def clearWindowTail(): Unit

  protected def newWindowBuffer(n: Int): Array[C]

  // ---- default implementations that can be overridden if `super` is called ----

  protected def onDone(inlet: Inlet[_]): Unit =
    if (inlet == hIn1.inlet || inlet == hIn2.inlet) {
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

  // ---- visible impl ----

  protected final var winBuf: Array[C] = _
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
        winBuf = if (_winBufSz == 0) null else newWindowBuffer(_winBufSz) // wTpe.newArray(_winBufSz)
      }

      readOff  = 0L
      readRem  = readWinSize
      stage     = 1
    }

    while (stage == 1) {
      val remIn = min(hIn1.available, hIn2.available)
      if (remIn == 0) return
      val numIn = min(remIn, readRem).toInt
      if (numIn > 0) readIntoWindow(numIn)
      readOff += numIn
      readRem -= numIn
      if (hIn1.isDone || hIn2.isDone) {
        flushStage1Enter2()
      } else if (readRem == 0) {
        enterStage2()
      }
    }

    while (stage == 2) {
      val remOut = hOut.available
      if (remOut == 0) return
      val numOut = min(remOut, writeRem).toInt
      if (numOut > 0) writeFromWindow(numOut)
      writeOff += numOut
      writeRem -= numOut
      if (writeRem == 0) {
        if (hIn1.isDone || hIn2.isDone) {
          if (hOut.flush()) completeStage()
          return
        } else {
          stage = 0
        }
      }
    }

    process()
  }
}
