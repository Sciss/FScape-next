/*
 *  RunningMax.scala
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
import de.sciss.fscape.stream.impl.{FilterIn2DImpl, RunningValueImpl, StageImpl, NodeImpl}

/*

  TODO --- check out this: http://arxiv.org/abs/cs/0610046

  (I haven't read it, but obviously if the window is sorted,
  we can drop, insert or query an element in O(log N)).

 */
object RunningMax {
  def apply(in: OutD, trig: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(trig, stage.in1)
    stage.out
  }

  private final val name = "RunningMax"

  private type Shape = FanInShape2[BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.trig"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with RunningValueImpl[Shape]
      with FilterIn2DImpl[BufD, BufI] {

    protected def neutralValue: Double = Double.NegativeInfinity

    protected def combine(a: Double, b: Double): Double = math.max(a, b)
  }
}