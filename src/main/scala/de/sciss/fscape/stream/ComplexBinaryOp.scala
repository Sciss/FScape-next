/*
 *  ComplexBinaryOp.scala
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

import akka.stream.stage.GraphStageLogic
import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn2DImpl, StageImpl, StageLogicImpl}

/** Binary operator assuming stream is complex signal (real and imaginary interleaved).
  * Outputs another complex stream even if the operator yields a purely real-valued result.
  */
object ComplexBinaryOp {
  import graph.ComplexBinaryOp.Op

  def apply(op: Op, a: OutD, b: OutD)(implicit builder: Builder): OutD = {
    val stage0  = new Stage(op)
    val stage   = builder.add(stage0)
    builder.connect(a, stage.in0)
    builder.connect(b, stage.in1)
    stage.out
  }

  private final val name = "ComplexBinaryOp"

  private type Shape = FanInShape2[BufD, BufD, BufD]

  private final class Stage(op: Op)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InD (s"$name.a" ),
      in1 = InD (s"$name.b" ),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(op, shape)
  }

  private final class Logic(op: Op, shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with FilterChunkImpl[BufD, BufD, Shape]
      with FilterIn2DImpl[BufD, BufD] {

    protected def processChunk(inOff: Int, outOff: Int, chunk0: Int): Int = {
      val chunk = chunk0 & ~1  // must be even
      op(a = bufIn0.buf, aOff = inOff, b = bufIn1.buf, bOff = inOff,
        out = bufOut0.buf, outOff = outOff, len = chunk >> 1)
      chunk
    }
  }
}