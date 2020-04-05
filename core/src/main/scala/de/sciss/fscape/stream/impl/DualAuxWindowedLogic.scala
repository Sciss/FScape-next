/*
 *  DualAuxWindowedLogic.scala
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
package impl

import akka.stream.Shape
import akka.stream.stage.GraphStageLogic

/** A logic component for windowed processing, where window parameters
  * are obtained "on demand", i.e. at the speed of one per window.
  *
  * "aux 2" serves as additional buffer to the main window processing,
  * and thus needs to be exhausted before main processing function is called.
  */
@deprecated("Should move to using Handlers", since = "2.35.1")
trait DualAuxWindowedLogic[S <: Shape] extends DualAuxChunkImpl[S] {

  _: GraphStageLogic =>

  // ---- abstract ----

  /** Notifies about the start of the next window.
    *
    * @return the number of frames to write to the internal window buffer
    *         (becomes `writeToWinRemain`)
    */
  protected def startNextWindow(): Long

  /** If crucial inputs have been closed. */
  protected def inputsEnded: Boolean

  /** Issues a copy from input buffer to internal window buffer.
    *
    * @param writeToWinOff  current offset into internal window buffer
    * @param chunk          number of frames to copy
    */
  protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit

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
  protected def processWindow(writeToWinOff: Long /* , flush: Boolean */): Long

  protected def processAux2(): Boolean

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
        writeToWinRemain  = startNextWindow()
        isNextWindow      = false
        stateChange       = true
        // logStream(s"startNextWindow(); writeToWinRemain = $writeToWinRemain")
      }

      val chunk     = math.min(writeToWinRemain, mainInRemain).toInt
      val flushIn   = flushIn0 && writeToWinOff > 0
      if (chunk > 0 || flushIn) {
        // logStream(s"writeToWindow(); inOff = $inOff, writeToWinOff = $writeToWinOff, chunk = $chunk")
        if (chunk > 0) {
          copyInputToWindow(writeToWinOff = writeToWinOff, chunk = chunk)
          mainInOff        += chunk
          mainInRemain     -= chunk
          writeToWinOff    += chunk
          writeToWinRemain -= chunk
          stateChange       = true
        }

        val aux2Ok = processAux2()

        if (aux2Ok && (writeToWinRemain == 0 || flushIn)) {
          readFromWinRemain = processWindow(writeToWinOff = writeToWinOff)
          writeToWinOff     = 0
          readFromWinOff    = 0
          isNextWindow      = true
          stateChange       = true
          aux1InOff        += 1
          aux1InRemain     -= 1
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

  protected final def shouldComplete(): Boolean = inputsEnded && writeToWinOff == 0 && readFromWinRemain == 0
}