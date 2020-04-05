/*
 *  Differentiate.scala
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

object Differentiate {
  def apply(in: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "Differentiate"

  private type Shp = FlowShape[BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FlowShape(
      in  = InD (s"$name.in"  ),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with FilterIn1DImpl [BufD]
      with FilterChunkImpl[BufD, BufD, Shp] {

    private[this] var xPrev = 0.0

    protected def processChunk(inOff: Int, outOff: Int, len: Int): Unit = {
      val b0      = bufIn0  .buf
      val out     = bufOut0 .buf
      var x1      = xPrev
      var inOffI  = inOff
      var outOffI = outOff
      val stop0   = inOff + len
      while (inOffI < stop0) {
        val x0        = b0(inOffI)
        val y0        = x0 - x1
        out(outOffI)  = y0
        inOffI       += 1
        outOffI      += 1
        x1            = x0
      }
      xPrev = x1
    }
  }
}