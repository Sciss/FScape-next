/*
 *  BinaryOp.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn2DImpl, NodeImpl, StageImpl}

object BinaryOp {
  import graph.BinaryOp.Op

  def apply(op: Op, in1: OutD, in2: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(op)
    val stage   = b.add(stage0)
    b.connect(in1, stage.in0)
    b.connect(in2, stage.in1)
    stage.out
  }

  private final val name = "BinaryOp"

  private type Shape = FanInShape2[BufD, BufD, BufD]

  private final class Stage(op: Op)(implicit ctrl: Control) extends StageImpl[Shape](s"$name(${op.name})") {
    val shape = new FanInShape2(
      in0 = InD (s"$name.in1"),
      in1 = InD (s"$name.in2"),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(op, shape)
  }

  private final class Logic(op: Op, shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(s"$name(${op.name})", shape)
      with FilterChunkImpl /* SameChunkImpl[Shape] */ [BufD, BufD, Shape]
      with FilterIn2DImpl /* BinaryInDImpl */[BufD, BufD] {

    private[this] var aVal: Double = _
    private[this] var bVal: Double = _

//    protected def shouldComplete(): Boolean = inRemain == 0 && isClosed(in0) && isClosed(in1)

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      var inOffI  = inOff
      var outOffI = outOff
      val inStop  = inOffI + chunk
      val a       = /* if (bufIn0 == null) null else */ bufIn0.buf
      val b       = if (bufIn1 == null) null else bufIn1.buf
      val aStop   = /* if (a == null) 0 else */ bufIn0.size
      val bStop   = if (b == null) 0 else bufIn1.size
      val out     = bufOut0.buf
      var av      = aVal
      var bv      = bVal
      while (inOffI < inStop) {
        if (inOffI < aStop) av = a(inOffI)
        if (inOffI < bStop) bv = b(inOffI)
        out(outOffI) = op(av, bv)
        inOffI  += 1
        outOffI += 1
      }
      aVal = av
      bVal = bv
    }
  }
}