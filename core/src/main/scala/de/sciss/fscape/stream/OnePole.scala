/*
 *  OnePole.scala
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

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn2DImpl, NodeImpl, StageImpl}

object OnePole {
  def apply(in: OutD, coef: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(coef, stage.in1)
    stage.out
  }

  private final val name = "OnePole"

  private type Shp = FanInShape2[BufD, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape2(
      in0 = InD (s"$name.in"  ),
      in1 = InD (s"$name.coef"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with FilterIn2DImpl [BufD, BufD]
      with FilterChunkImpl[BufD, BufD, Shp] {

    private[this] var coefY   = 0.0
    private[this] var coefX   = 0.0
    private[this] var yPrev   = 0.0

    protected def processChunk(inOff: Int, outOff: Int, len: Int): Unit = {
      val b0      = bufIn0.buf
      val b1      = if (bufIn1 == null) null else bufIn1.buf
      val stop1   = if (b1     == null) 0    else bufIn1.size
      val out     = bufOut0.buf
      var cy      = coefY
      var cx      = coefX
      var y0      = yPrev
      var inOffI  = inOff
      var outOffI = outOff
      val stop0   = inOff + len
      while (inOffI < stop0) {
        if (inOffI < stop1) {
          cy = b1(inOffI)
          cx = 1.0 - math.abs(cy)
        }
        val x0        = b0(inOffI)
        y0            = (cx * x0) + (cy * y0)
        out(outOffI)  = y0
        inOffI       += 1
        outOffI      += 1
      }
      coefY = cy
      coefX = cx
      yPrev = y0
    }
  }
}