/*
 *  RunningMax.scala
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

import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.FilterIn2Impl

object RunningMax {
  def apply(in: OutD, trig: OutI)(implicit b: Builder): OutD = {
    ???
  }

  private final class Stage(implicit ctrl: Control)
    extends GraphStage[FanInShape2[BufD, BufI, BufD]] {

    val shape = new FanInShape2(
      in0 = InD ("RunningMax.in"  ),
      in1 = InI ("RunningMax.trig"),
      out = OutD("RunningMax.out" )
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(protected val shape: FanInShape2[BufD, BufI, BufD])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with FilterIn2Impl[BufD, BufI, BufD] {

    private[this] var inOff             = 0  // regarding `bufIn`
    private[this] var inRemain          = 0
    private[this] var outOff            = 0  // regarding `bufOut`
    private[this] var outRemain         = 0
    private[this] var outSent           = true

    @inline
    private[this] def allocOutBuf(): BufD = ctrl.borrowBufD()

    @inline
    private[this] def shouldRead = inRemain == 0 && canRead

    protected def processChunk(len: Int): Unit = ???

    def process(): Unit = {
      var stateChange = false

      if (shouldRead) {
        readIns()
        inRemain    = bufIn0.size
        inOff       = 0
        stateChange = true
      }

      if (outSent) {
        bufOut        = allocOutBuf()
        outRemain     = bufOut.size
        outOff        = 0
        outSent       = false
        stateChange   = true
      }

      val chunk = math.min(inRemain, outRemain)
      if (chunk > 0) {
        processChunk(chunk)
        inOff       += chunk
        inRemain    -= chunk
        outOff      += chunk
        outRemain   -= chunk
        stateChange  = true
      }

      val flushOut = inRemain == 0 && isClosed(shape.in0)
      if (!outSent && (outRemain == 0 || flushOut) && isAvailable(shape.out)) {
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
}