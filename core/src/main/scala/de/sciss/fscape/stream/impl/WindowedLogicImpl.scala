/*
 *  WindowedLogicImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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

trait WindowedLogicImpl[S <: Shape] extends ChunkImpl[S] {

  _: GraphStageLogic =>

  // ---- abstract ----

  /** Notifies about the start of the next window.
    *
    * @param inOff  current offset into input buffer
    * @return the number of frames to write to the internal window buffer
    *         (becomes `writeToWinRemain`)
    */
  protected def startNextWindow(inOff: Int): Long

  /** If crucial inputs have been closed. */
  protected def inputsEnded: Boolean

  /** Issues a copy from input buffer to internal window buffer.
    *
    * @param inOff          current offset into input buffer
    * @param writeToWinOff  current offset into internal window buffer
    * @param chunk          number of frames to copy
    */
  protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit

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
  protected def processWindow(writeToWinOff: Long): Long

  protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit

  // ---- impl ----

  private[this] final var writeToWinOff     = 0L
  private[this] final var writeToWinRemain  = 0L
  private[this] final var readFromWinOff    = 0L
  private[this] final var readFromWinRemain = 0L
  private[this] final var isNextWindow      = true

  @inline
  private[this] final def canWriteToWindow  = readFromWinRemain == 0 && inValid

  protected final def processChunk(): Boolean = {
    var stateChange = false

    if (canWriteToWindow) {
      val flushIn0 = inputsEnded // inRemain == 0 && shouldComplete()
      if (isNextWindow && !flushIn0) {
        writeToWinRemain  = startNextWindow(inOff = inOff)
        isNextWindow      = false
        stateChange       = true
        // logStream(s"startNextWindow(); writeToWinRemain = $writeToWinRemain")
      }

      val chunk     = math.min(writeToWinRemain, inRemain).toInt
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
          writeToWinOff     = 0
          readFromWinOff    = 0
          isNextWindow      = true
          stateChange       = true
          // logStream(s"processWindow(); readFromWinRemain = $readFromWinRemain")
        }
      }
    }

    if (readFromWinRemain > 0) {
      val chunk = math.min(readFromWinRemain, outRemain).toInt
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

    stateChange
  }

  protected final def shouldComplete(): Boolean = inputsEnded && writeToWinOff /* writeToWinRemain */ == 0 && readFromWinRemain == 0
}