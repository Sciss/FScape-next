/*
 *  ExpExp.scala
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

import akka.stream.{Attributes, FanInShape5}
import de.sciss.fscape.stream.impl.Handlers._
import de.sciss.fscape.stream.impl.{NodeImpl, RangeScaleImpl, StageImpl}

import scala.math.{log, pow}

object ExpExp {
  def apply(in: OutD, inLow: OutD, inHigh: OutD, outLow: OutD, outHigh: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in      , stage.in0)
    b.connect(inLow   , stage.in1)
    b.connect(inHigh  , stage.in2)
    b.connect(outLow  , stage.in3)
    b.connect(outHigh , stage.in4)
    stage.out
  }

  private final val name = "ExpExp"

  private type Shp = FanInShape5[BufD, BufD, BufD, BufD, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape5(
      in0 = InD (s"$name.in"      ),
      in1 = InD (s"$name.inLow"   ),
      in2 = InD (s"$name.inHigh"  ),
      in3 = InD (s"$name.outLow"  ),
      in4 = InD (s"$name.outHigh" ),
      out = OutD(s"$name.out"     )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends RangeScaleImpl(name, layer, shape) {

    protected def run(in: Array[Double], inOff0: Int, out: Array[Double], outOff0: Int, num: Int,
                      hInLo: InDAux, hInHi: InDAux, hOutLo: InDAux, hOutHi: InDAux): Unit = {
      val stop    = inOff0 + num
      var inOff   = inOff0
      var outOff  = outOff0
      while (inOff < stop) {
        val inLo    = hInLo  .next()
        val inHi    = hInHi  .next()
        val outLo   = hOutLo .next()
        val outHi   = hOutHi .next()

        val powBase = outHi / outLo
        val powArg  = log(in(inOff) / inLo) / log(inHi / inLo)
        out(outOff) = pow(powBase, powArg) * outLo

        inOff  += 1
        outOff += 1
      }
    }
  }
}