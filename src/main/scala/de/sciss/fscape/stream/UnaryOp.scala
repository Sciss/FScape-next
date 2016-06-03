/*
 *  UnaryOp.scala
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
import akka.stream.{Attributes, FlowShape}
import de.sciss.fscape.stream.impl.FilterIn1Impl

object UnaryOp {
  import graph.UnaryOp.Op

  def apply(op: Op, in: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(op)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final class Stage(op: Op)(implicit ctrl: Control)
    extends GraphStage[FlowShape[BufD, BufD]] {

    val shape = new FlowShape(
      in  = InD ("UnaryOp.in" ),
      out = OutD("UnaryOp.out")
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(op, shape)
  }

  private final class Logic(op: Op,
                            protected val shape: FlowShape[BufD, BufD])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with FilterIn1Impl[BufD, BufD] {

    private[this] var inOff             = 0  // regarding `bufIn`
    private[this] var inRemain          = 0
    private[this] var outOff            = 0  // regarding `bufOut`
    private[this] var outRemain         = 0
    private[this] var outSent           = true

    @inline
    private[this] def allocOutBuf(): BufD = ctrl.borrowBufD()

    @inline
    private[this] def shouldRead = inRemain == 0 && canRead

    def process(): Unit = {
      var stateChange = false

      if (shouldRead) {
        readIns()
        inRemain    = bufIn.size
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
        var inOffI  = inOff
        var outOffI = outOff
        val inStop  = inOffI + chunk
        val in      = bufIn .buf
        val out     = bufOut.buf
        while (inOffI < inStop) {
          out(outOffI) = op(in(inOffI))
          inOffI  += 1
          outOffI += 1
        }
        inOff        = inOffI
        inRemain    -= chunk
        outOff       = outOffI
        outRemain   -= chunk
        stateChange  = true
      }

      val flushOut = inRemain == 0 && isClosed(shape.in)
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