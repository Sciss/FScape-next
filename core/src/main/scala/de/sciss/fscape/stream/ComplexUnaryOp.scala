/*
 *  ComplexUnaryOp.scala
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

import akka.stream.{Attributes, FlowShape}
import de.sciss.fscape.stream.impl.deprecated.{FilterChunkImpl, FilterIn1DImpl}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

/** Unary operator assuming stream is complex signal (real and imaginary interleaved).
  * Outputs another complex stream even if the operator yields a purely real-valued result
  * (ex. `abs`).
  *
  * XXX TODO - need more ops such as conjugate, polar-to-cartesian, ...
  */
object ComplexUnaryOp {
  import graph.ComplexUnaryOp.Op

  def apply(op: Op, in: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer, op)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "ComplexUnaryOp"

  private type Shp = FlowShape[BufD, BufD]

  private final class Stage(layer: Layer, op: Op)(implicit ctrl: Control) extends StageImpl[Shp](s"$name(${op.name})") {
    val shape: Shape = new FlowShape(
      in  = InD (s"$name.in" ),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer, op)
  }

  private final class Logic(shape: Shp, layer: Layer, op: Op)(implicit ctrl: Control)
    extends NodeImpl(s"$name(${op.name})", layer, shape)
      with FilterChunkImpl[BufD, BufD, Shp]
      with FilterIn1DImpl[BufD] {

//   override def process(): Unit = {
//     println(s"process() $this; inRemain $inRemain, outRemain $outRemain, outSent $outSent, canRead $canRead, canWrite $canWrite")
//     super.process()
//   }

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      require((chunk & 1) == 0)// must be even
      op(in = bufIn0.buf, inOff = inOff, out = bufOut0.buf, outOff = outOff, len = chunk >> 1)
    }
  }
}