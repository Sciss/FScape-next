/*
 *  RunningValueImpl.scala
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

@deprecated("Should move to using Handlers", since = "2.35.1")
trait RunningValueImpl[S <: Shape] extends FilterChunkImpl[BufD, BufD, S] {
  _: GraphStageLogic =>

  // ---- abstract ----

  protected def neutralValue: Double

  protected def combine(a: Double, b: Double): Double

  protected def bufIn0 : BufD
  protected def bufIn1 : BufI
  protected def bufOut0: BufD

  // ---- impl ----

  private[this] var value = neutralValue
  private[this] var trig0 = false

  protected final def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
    var inOffI  = inOff
    var outOffI = outOff
    val stop0   = inOffI + chunk
    val b0      = bufIn0.buf
    val b1      = if (bufIn1 == null) null else bufIn1.buf
    val out     = bufOut0.buf
    val stop1   = if (b1 == null) 0 else bufIn1.size
    var v       = value
    var t0      = trig0
    var t1      = t0
    while (inOffI < stop0) {
      val x0 = b0(inOffI)
      if (inOffI < stop1) t1 = !t0 && b1(inOffI) > 0
      v = if (t1) x0 else combine(v, x0)
      out(outOffI) = v
      inOffI  += 1
      outOffI += 1
      t0       = t1
    }
    value = v
    trig0 = t0
  }
}