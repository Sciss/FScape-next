package de.sciss.fscape.stream.impl

import akka.stream.{Inlet, Shape}
import de.sciss.fscape.logStream
import de.sciss.fscape.stream.{BufElem, Node, StreamType}

import scala.annotation.tailrec

/** Ok, '''another''' (third) attempt to isolate a correct building block.
  * This is for window processing UGens where window parameters include
  * `winSize` and possibly others, and will be polled per window.
  */
trait WindowedLogicNew[A, E >: Null <: BufElem[A], S <: Shape] extends Node {
  _: Handlers[S] =>

  // ---- abstract ----

  protected def aTpe  : StreamType[A, E]

  protected def hIn   : Handlers.AbstractInMain [A, E]
  protected def hOut  : Handlers.AbstractOutMain[A, E]

  protected def tryObtainWinParams(): Boolean

  /** The size for the window buffer, or zero if this buffer should no be used. */
  protected def winBufSize: Int

  // ---- default implementations that can be overridden if `super` is called ----

  protected def onDone(inlet: Inlet[_]): Unit =
    if (inlet == hIn.inlet) {
      if (stage == 0 || (stage == 1 && readOff == 0)) {
        stage = 2
        if (hOut.flush()) completeStage()
      }
    }

  override protected def stopped(): Unit = {
    super.stopped()
    winBuf = null
  }

  // ---- default implementations that can be overridden ----

  /** The default number of frames to read in per window equals the window buffer size */
  protected def readWinSize : Long = winBufSize

  /** The default number of frames to write out per window equals the window buffer size */
  protected def writeWinSize: Long = winBufSize

  /** The default buffer transformation, called after a window has been fully read in, is a no-op */
  protected def processWindow(): Unit = ()

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

  /** The default implementation clears from `readOff` to the end of the window buffer. */
  protected def clearWindowTail(): Unit = {
    val _buf = winBuf
    if (_buf != null && _buf.length > readOff) {
      val offI = readOff.toInt
      aTpe.clear(winBuf, offI, _buf.length - offI)
    }
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
        if (_winBufSz > 0) winBuf = aTpe.newArray(_winBufSz)
      }

      readOff  = 0
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
          if (readRem > 0) clearWindowTail()
          readOff += readRem
          readRem  = 0
        }

        if (readRem == 0) {
          processWindow()
          writeOff    = 0
          writeRem    = writeWinSize
          stage       = 2
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
