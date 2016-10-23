/*
 *  RunningWindowValueImpl.scala
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

trait RunningWindowValueImpl[S <: Shape]
  extends FilterLogicImpl[BufD, S]
  with WindowedLogicImpl[S] {
  _: GraphStageLogic =>

  // ---- abstract ----

  protected def combine(a: Double, b: Double): Double

  protected def bufIn0 : BufD
  protected def bufIn1 : BufI
  protected def bufIn2 : BufI
  protected def bufOut0: BufD

  // ---- impl ----

//  private[this] var value = neutralValue
  private[this] var trig0 = false

  private[this] var winSize: Int = _
  private[this] var winBuf : Array[Double] = _

  protected final def startNextWindow(inOff: Int): Long = {
    val oldSize = winSize
    if (bufIn1 != null && inOff < bufIn1.size) {
      winSize = math.max(1, bufIn1.buf(inOff))
    }
    if (winSize != oldSize) {
      winBuf = new Array[Double](winSize)
      trig0 = true
    } else {
      trig0 = false
    }
    winSize
  }

  protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit =
    Util.copy(winBuf, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)

  protected final def processWindow(writeToWinOff: Long): Long = writeToWinOff

  // XXX TODO --- this is very similar to processChunk in RunningValueImpl; perhaps DRY?
  protected final def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit = {
    // Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff, chunk)
    var inOffI  = inOff
    var outOffI = writeToWinOff.toInt
    val stop0   = inOffI + chunk
    val b0      = bufIn0.buf
    val b2      = if (bufIn2 == null) null else bufIn2.buf
    val out     = winBuf
    val stop2   = if (b2     == null) 0    else bufIn2.size
    var t0      = trig0
    while (inOffI < stop0) {
      val x0 = b0(inOffI)
      if (!t0 && inOffI < stop2 && b2(inOffI) > 0) t0 = true
      val v = if (t0) x0 else combine(out(outOffI), x0)
      out(outOffI) = v
      inOffI  += 1
      outOffI += 1
    }
    trig0 = t0
  }
}