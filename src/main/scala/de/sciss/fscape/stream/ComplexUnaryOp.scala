/*
 *  ComplexUnaryOp.scala
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

/** Unary operator assuming stream is complex signal (real and imaginary interleaved).
  * Outputs another complex stream even if the operator yields a purely real-valued result
  * (ex. `abs`).
  *
  * XXX TODO - need more ops such as conjugate, polar-to-cartesian, ...
  */
object ComplexUnaryOp {
  import graph.ComplexUnaryOp.Op

  def apply(op: Op, in: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(op)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "ComplexUnaryOp"

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

    protected def processChunk(inOff: Int, outOff: Int, chunk0: Int): Int = {
      val chunk = chunk0 & ~1  // must be even
      op(in = bufIn0.buf, inOff = inOff, out = bufOut.buf, outOff = outOff, len = chunk >> 1)
      chunk
    }
  }
}