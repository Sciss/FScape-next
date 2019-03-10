/*
 *  SetResetFF.scala
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
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn2IImpl, NodeImpl, StageImpl}

object SetResetFF {
  def apply(trig: OutI, reset: OutI)(implicit b: Builder): OutI = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(trig , stage.in0)
    b.connect(reset, stage.in1)
    stage.out
  }

  private final val name = "SetResetFF"

  private type Shape = FanInShape2[BufI, BufI, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InI (s"$name.trig" ),
      in1 = InI (s"$name.reset"),
      out = OutI(s"$name.out"  )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with FilterIn2IImpl[BufI, BufI]
      with FilterChunkImpl[BufI, BufI, Shape] {

    private[this] var highT     = false
    private[this] var highR     = false
    private[this] var state     = false

    protected def processChunk(inOff: Int, outOff: Int, len: Int): Unit = {
      val b0      = bufIn0 .buf
      val b1      = if (bufIn1 == null) null else bufIn1.buf
      val stop1   = if (b1     == null) 0    else bufIn1.size
      val out     = bufOut0.buf
      var h0t     = highT
      var h0r     = highR
      var h1t     = false
      var h1r     = false
      var s0      = state
      var inOffI  = inOff
      var outOffI = outOff
      val stop0   = inOff + len
      while (inOffI < stop0) {
        h1t = b0(inOffI) > 0
        if (h1t && !h0t) {
          s0 = true
        }
        if (inOffI < stop1) h1r = b1(inOffI) > 0
        if (h1r && !h0r) {
          s0 = false
        }
        out(outOffI) = if (s0) 1 else 0
        inOffI  += 1
        outOffI += 1
        h0t      = h1t
        h0r      = h1r
      }
      highT = h0t
      highR = h0r
      state = s0
    }
  }
}