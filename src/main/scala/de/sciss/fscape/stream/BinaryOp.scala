/*
 *  BinaryOp.scala
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

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(op, shape)
  }

  private final class Logic(op: Op, shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(s"$name(${op.name})", shape)
      with FilterChunkImpl[BufD, BufD, Shape]
      with FilterIn2DImpl[BufD, BufD] {

    private[this] var bVal: Double = _

    var LAST_BUF: BufD = null

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      if (LAST_BUF != bufIn1) {
        LAST_BUF = bufIn1
        println(s"bin    : ${bufIn1.hashCode.toHexString} - ${bufIn1.buf.toVector.hashCode.toHexString}")
      }

      var inOffI  = inOff
      var outOffI = outOff
      val aStop   = inOffI + chunk
      val a       = bufIn0.buf
      val b       = if (bufIn1 == null) null else bufIn1.buf
      val out     = bufOut0.buf
      val bStop   = if (b      == null) 0    else bufIn1.size
      var bv      = bVal
      while (inOffI < aStop) {
        val                 av = a(inOffI)
        if (inOffI < bStop) bv = b(inOffI)
        out(outOffI) = op(av, bv)
        inOffI  += 1
        outOffI += 1
      }
      bVal = bv
    }
  }
}