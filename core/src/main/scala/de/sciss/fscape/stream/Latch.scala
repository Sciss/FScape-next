/*
 *  Latch.scala
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

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn2DImpl, StageImpl, NodeImpl}

object Latch {
  def apply(in: OutD, gate: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(gate, stage.in1)
    stage.out
  }

  private final val name = "Latch"

  private type Shape = FanInShape2[BufD, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.gate"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with FilterIn2DImpl[BufD, BufI]
      with FilterChunkImpl[BufD, BufD, Shape] {

    private[this] var high  = false
    private[this] var held  = 0.0

    protected def processChunk(inOff: Int, outOff: Int, len: Int): Unit = {
      val b0      = bufIn0.buf
      val b1      = if (bufIn1 == null) null else bufIn1.buf
      val stop1   = if (b1     == null) 0    else bufIn1.size
      val out     = bufOut0.buf
      var h0      = high
      var v0      = held
      var inOffI  = inOff
      var outOffI = outOff
      val stop0   = inOff + len
      while (inOffI < stop0) {
        if (inOffI < stop1) h0 = b1(inOffI) > 0
        if (h0) {
          v0 = b0(inOffI)
        }
        out(outOffI) = v0
        inOffI  += 1
        outOffI += 1
      }
      high = h0
      held = v0
    }
  }
}