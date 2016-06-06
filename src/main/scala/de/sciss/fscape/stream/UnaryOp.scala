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
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn1Impl}

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

  private final class Logic(op: Op, protected val shape: FlowShape[BufD, BufD])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with FilterChunkImpl[BufD, BufD, FlowShape[BufD, BufD]]
      with FilterIn1Impl[BufD, BufD] {

    protected def allocOutBuf(): BufD = ctrl.borrowBufD()

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Int = {
      var inOffI  = inOff
      var outOffI = outOff
      val inStop  = inOffI + chunk
      val in      = bufIn0 .buf
      val out     = bufOut.buf
      while (inOffI < inStop) {
        out(outOffI) = op(in(inOffI))
        inOffI  += 1
        outOffI += 1
      }
      chunk
    }
  }
}