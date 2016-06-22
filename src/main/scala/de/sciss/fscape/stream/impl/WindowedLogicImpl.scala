/*
 *  WindowedLogicImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream
package impl

import akka.stream.Shape
import akka.stream.stage.GraphStageLogic

import scala.annotation.tailrec

trait WindowedLogicImpl[In0 >: Null <: BufLike, S <: Shape]
  extends InOutImpl[S] {

  _: GraphStageLogic =>

  // ---- abstract ----

  /** Notifies about the start of the next window.
    *
    * @param inOff  current offset into input buffer
    * @return the number of frames to write to the internal window buffer
    *         (becomes `writeToWinRemain`)
    */
  protected def startNextWindow(inOff: Int): Int

  /** If crucial inputs have been closed. */
  protected def shouldComplete(): Boolean

  /** Issues a copy from input buffer to internal window buffer.
    *
    * @param inOff          current offset into input buffer
    * @param writeToWinOff  current offset into internal window buffer
    * @param chunk          number of frames to copy
    */
  protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit

  /** Called when the internal window buffer is full, in order to
    * proceed to the next phase of copying from window to output.
    * (transitioning between `copyInputToWindow` and `copyWindowToOutput`)
    *
    * @param writeToWinOff  the current offset into the internal window buffer.
    *                       this is basically the amount of frames available for
    *                       processing.
    * @return the number of frames available for sending through `copyWindowToOutput`
    *         (this becomes `readFromWinRemain`).
    */
  protected def processWindow(writeToWinOff: Int /* , flush: Boolean */): Int

  protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit

  protected def allocOutputBuffers(): Int

  // ---- impl ----

  private[this] final var writeToWinOff     = 0
  private[this] final var writeToWinRemain  = 0
  private[this] final var readFromWinOff    = 0
  private[this] final var readFromWinRemain = 0
  private[this] final var inOff             = 0  // regarding `bufIn`
  private[this] final var inRemain          = 0
  private[this] final var outOff            = 0  // regarding `bufOut`
  private[this] final var outRemain         = 0

  private[this] final var outSent           = true
  private[this] final var isNextWindow      = true

  @inline
  private[this] final def shouldRead        = inRemain          == 0 && canRead
  @inline
  private[this] final def canWriteToWindow  = readFromWinRemain == 0 && inValid

  @tailrec
  final def process(): Unit = {
    // becomes `true` if state changes,
    // in that case we run this method again.
    var stateChange = false
    logStream(s"process() $this")

    if (shouldRead) {
      inRemain    = readIns()
      inOff       = 0
      stateChange = true
      // logStream(s"readIns(); inRemain = $inRemain")
    }

    if (canWriteToWindow) {
      val flushIn0 = inRemain == 0 && shouldComplete()
      if (isNextWindow && flushIn0) {
        writeToWinRemain  = startNextWindow(inOff = inOff)
        writeToWinOff     = 0
        isNextWindow      = false
        stateChange       = true
        // logStream(s"startNextWindow(); writeToWinRemain = $writeToWinRemain")
      }

      val chunk     = math.min(writeToWinRemain, inRemain)
      val flushIn   = flushIn0 && writeToWinOff > 0
      if (chunk > 0 || flushIn) {
        // logStream(s"writeToWindow(); inOff = $inOff, writeToWinOff = $writeToWinOff, chunk = $chunk")
        if (chunk > 0) {
          copyInputToWindow(inOff = inOff, writeToWinOff = writeToWinOff, chunk = chunk)
          inOff            += chunk
          inRemain         -= chunk
          writeToWinOff    += chunk
          writeToWinRemain -= chunk
          stateChange       = true
        }

        if (writeToWinRemain == 0 || flushIn) {
          readFromWinRemain = processWindow(writeToWinOff = writeToWinOff) // , flush = flushIn)
          readFromWinOff    = 0
          isNextWindow      = true
          stateChange       = true
          // logStream(s"processWindow(); readFromWinRemain = $readFromWinRemain")
        }
      }
    }

    if (readFromWinRemain > 0) {
      if (outSent) {
        outRemain     = allocOutputBuffers()
        outOff        = 0
        outSent       = false
        stateChange   = true
        // logStream(s"allocOutBuf(); outRemain = $outRemain")
      }

      val chunk = math.min(readFromWinRemain, outRemain)
      if (chunk > 0) {
        // logStream(s"readFromWindow(); readFromWinOff = $readFromWinOff, outOff = $outOff, chunk = $chunk")
        copyWindowToOutput(readFromWinOff = readFromWinOff, outOff = outOff, chunk = chunk)
        readFromWinOff    += chunk
        readFromWinRemain -= chunk
        outOff            += chunk
        outRemain         -= chunk
        stateChange        = true
      }
    }

    val flushOut = inRemain == 0 && writeToWinRemain == 0 && readFromWinRemain == 0 && shouldComplete()
    if (!outSent && (outRemain == 0 || flushOut) && canWrite) {
      writeOuts(outOff)
      outSent     = true
      stateChange = true
    }

    if (flushOut && outSent) {
      logStream(s"completeStage() $this")
      completeStage()
    }
    else if (stateChange) process()
  }
}