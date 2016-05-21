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

package de.sciss.fscape.stream
package impl

import akka.stream.FanInShape
import akka.stream.stage.GraphStageLogic

import scala.annotation.tailrec

trait WindowedLogicImpl[In0 >: Null <: BufLike, Out >: Null <: BufLike, Shape <: FanInShape[Out]]
  extends InOutImpl[Shape] {

  _: GraphStageLogic =>

  // ---- abstract ----

  protected def startNextWindow(inOff: Int): Int

  /** If crucial inputs have been closed. */
  protected def shouldComplete(): Boolean

  /** Number of samples available from input buffers. */
  protected def inAvailable(): Int

  protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit

  protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit

  protected def processWindow(writeToWinOff: Int): Int

  protected def allocOutBuf(): Out

  protected var bufOut: Out

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
      readIns()
      inRemain    = inAvailable()
      inOff       = 0
      stateChange = true
      logStream(s"readIns(); inRemain = ${inAvailable()}")
    }

    if (canWriteToWindow) {
      if (isNextWindow) {
        writeToWinRemain  = startNextWindow(inOff = inOff)
        writeToWinOff     = 0
        isNextWindow      = false
        stateChange       = true
        logStream(s"startNextWindow(); writeToWinRemain = $writeToWinRemain")
      }

      val chunk     = math.min(writeToWinRemain, inRemain)
      val flushIn   = inRemain == 0 && writeToWinOff > 0 && shouldComplete()
      if (chunk > 0 || flushIn) {
        logStream(s"writeToWindow(); inOff = $inOff, writeToWinOff = $writeToWinOff, chunk = $chunk")
        if (chunk > 0) {
          copyInputToWindow(inOff = inOff, writeToWinOff = writeToWinOff, chunk = chunk)
          inOff            += chunk
          inRemain         -= chunk
          writeToWinOff    += chunk
          writeToWinRemain -= chunk
          stateChange       = true
        }

        if (writeToWinRemain == 0 || flushIn) {
          readFromWinRemain = processWindow(writeToWinOff = writeToWinOff)
          readFromWinOff    = 0
          isNextWindow      = true
          stateChange       = true
          logStream(s"processWindow(); readFromWinRemain = $readFromWinRemain")
        }
      }
    }

    if (readFromWinRemain > 0) {
      if (outSent) {
        bufOut        = allocOutBuf()
        outRemain     = bufOut.size
        outOff        = 0
        outSent       = false
        stateChange   = true
        logStream(s"allocOutBuf(); outRemain = $outRemain")
      }

      val chunk = math.min(readFromWinRemain, outRemain)
      if (chunk > 0) {
        logStream(s"readFromWindow(); readFromWinOff = $readFromWinOff, outOff = $outOff, chunk = $chunk")
        copyWindowToOutput(readFromWinOff = readFromWinOff, outOff = outOff, chunk = chunk)
        readFromWinOff    += chunk
        readFromWinRemain -= chunk
        outOff            += chunk
        outRemain         -= chunk
        stateChange        = true
      }
    }

    val flushOut = inRemain == 0 && writeToWinOff == 0 && readFromWinRemain == 0 && shouldComplete()
    if (!outSent && (outRemain == 0 || flushOut) && isAvailable(shape.out)) {
      logStream(s"sendOut(); outOff = $outOff")
      if (outOff > 0) {
        bufOut.size = outOff
        push(shape.out, bufOut)
      } else {
        bufOut.release()
      }
      bufOut      = null
      outSent     = true
      stateChange = true
    }

    if      (flushOut && outSent) completeStage()
    else if (stateChange)         process()
  }
}