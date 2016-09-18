/*
 *  PollImpl.scala
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

import akka.stream.stage.GraphStageLogic

/** Common building block for `Poll` and `Progress`. */
trait PollImpl[In0 >: Null <: BufLike] extends Sink2Impl[In0, BufI] {
  _ : GraphStageLogic with Node =>

  // ---- abstract ----

  protected def trigger(buf: In0, off: Int): Unit

  // ---- impl ----

  private[this] var high0 = false

  protected final def shouldComplete = isClosed(shape.in0) && !isAvailable(shape.in0)

  final def process(): Unit = {
    logStream(s"process() $this")

    if (canRead) {
      val stop0   = readIns()
      val b0      = bufIn0
      val b1      = if (bufIn1 == null) null else bufIn1.buf
      val stop1   = if (b1     == null) 0    else bufIn1.size
      var h0      = high0
      var h1      = h0
      var inOffI  = 0
      while (inOffI < stop0) {
        if (inOffI < stop1) h1 = b1(inOffI) > 0
        if (h1 && !h0) {
          trigger(b0, inOffI)
        }
        inOffI  += 1
        h0       = h1
      }
      high0 = h0
    }

    if (shouldComplete) {
      logStream(s"completeStage() $this")
      completeStage()
    }
  }
}