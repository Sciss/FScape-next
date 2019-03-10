/*
 *  UnaryOp.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn1DImpl, StageImpl, NodeImpl}

object UnaryOp {
  import graph.UnaryOp.Op

  def apply(op: Op, in: OutD)(implicit b: Builder): OutD = {
    // println(s"UnaryOp($op, $in)")
    val stage0  = new Stage(b.layer, op)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "UnaryOp"

  private type Shape = FlowShape[BufD, BufD]

  private final class Stage(layer: Layer, op: Op)(implicit ctrl: Control)
    extends StageImpl[Shape](s"$name(${op.name})") {

    val shape = new FlowShape(
      in  = InD (s"$name.in" ),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer, op)
  }

  private final class Logic(shape: Shape, layer: Layer, op: Op)(implicit ctrl: Control)
    extends NodeImpl(s"$name(${op.name})", layer, shape)
      with FilterChunkImpl[BufD, BufD, Shape]
      with FilterIn1DImpl[BufD] {

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      // println(s"UnaryOp($op).processChunk(in $bufIn0, out $bufOut, chunk $chunk)")
      var inOffI  = inOff
      var outOffI = outOff
      val inStop  = inOffI + chunk
      val in      = bufIn0.buf
      val out     = bufOut0.buf
      while (inOffI < inStop) {
        out(outOffI) = op(in(inOffI))
        inOffI  += 1
        outOffI += 1
      }
    }
  }
}