/*
 *  RunningProduct.scala
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
import de.sciss.fscape.stream.impl.deprecated.FilterIn2DImpl
import de.sciss.fscape.stream.impl.{NodeImpl, RunningValueImpl, StageImpl}

object RunningProduct {
  def apply(in: OutD, trig: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(trig, stage.in1)
    stage.out
  }

  private final val name = "RunningProduct"

  private type Shp = FanInShape2[BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape2(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.trig"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with RunningValueImpl[Shp]
      with FilterIn2DImpl[BufD, BufI] {

    protected def neutralValue: Double = 1.0

    protected def combine(a: Double, b: Double): Double = a * b
  }
}