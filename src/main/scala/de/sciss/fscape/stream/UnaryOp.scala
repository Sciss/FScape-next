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
    // println(s"UnaryOp($op, $in)")
    val stage0  = new Stage(op)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "UnaryOp"

  private final class Stage(op: Op)(implicit ctrl: Control)
    extends GraphStage[FlowShape[BufD, BufD]] {

    override def toString = s"$name($op)@${hashCode.toHexString}"

    val shape = new FlowShape(
      in  = InD (s"$name.in" ),
      out = OutD(s"$name.out")
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(op, shape)
  }

  private final class Logic(op: Op, protected val shape: FlowShape[BufD, BufD])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with FilterChunkImpl[BufD, BufD, FlowShape[BufD, BufD]]
      with FilterIn1Impl[BufD, BufD] {

    override def toString = s"$name-L($op)@${hashCode.toHexString}"

    protected def allocOutBuf(): BufD = ctrl.borrowBufD()

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Int = {
      // println(s"UnaryOp($op).processChunk(in $bufIn0, out $bufOut, chunk $chunk)")
      var inOffI  = inOff
      var outOffI = outOff
      val inStop  = inOffI + chunk
      val in      = bufIn0.buf
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