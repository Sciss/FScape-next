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
import de.sciss.fscape.stream.impl.logic.FilterInAOutB
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

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
    extends FilterInAOutB[Double, BufD, Double, BufD, Shp](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hCoef = Handlers.InDAux(this, shape.in1)()
    private[this] var yPrev = 0.0

    protected def auxAvailable: Int = hCoef.available

    protected def run(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
      val coef    = hCoef   // XXX TODO --- could optimize by using `InDMain` instead.
      var y0      = yPrev
      var inOffI  = inOff
      var outOffI = outOff
      val stop0   = inOff + len
      while (inOffI < stop0) {
        val cy        = coef.next()
        val cx        = 1.0 - math.abs(cy)
        val x0        = in(inOffI)
        y0            = (cx * x0) + (cy * y0)
        out(outOffI)  = y0
        inOffI       += 1
        outOffI      += 1
      }
      yPrev = y0
    }
  }
}