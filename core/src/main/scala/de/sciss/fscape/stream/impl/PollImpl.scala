/*
 *  PollImpl.scala
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

import akka.stream.stage.GraphStageLogic
import de.sciss.fscape.logStream
import de.sciss.fscape.stream.{BufI, BufLike, Node}

/** Common building block for `Poll` and `Progress`. */
trait PollImpl[In0 >: Null <: BufLike] extends Sink2Impl[In0, BufI] {
  _ : GraphStageLogic with Node =>

  // ---- abstract ----

  protected def trigger(buf: In0, off: Int): Unit

  // ---- impl ----

  private[this] var high0 = false

  protected final def shouldComplete: Boolean = isClosed(shape.in0) && !isAvailable(shape.in0)

  final def process(): Unit = {
    logStream(s"process() $this")

    if (canRead) {
      val stop0   = readIns()
      val b0      = bufIn0
      val b1      = if (bufIn1 == null) null else bufIn1.buf
      val stop1   = if (b1     == null) 0    else bufIn1.size
//      var h0      = high0
      var h1      = high0 // h0
      var inOffI  = 0
      while (inOffI < stop0) {
        if (inOffI < stop1) h1 = b1(inOffI) > 0
        if (h1 /*&& !h0*/) {
          trigger(b0, inOffI)
        }
        inOffI  += 1
//        h0       = h1
      }
      high0 = h1 // h0
    }

    if (shouldComplete) {
      logStream(s"completeStage() $this")
      completeStage()
    }
  }
}