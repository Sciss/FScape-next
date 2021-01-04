/*
 *  RunningMin.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{NodeImpl, RunningValueImpl, StageImpl}

object RunningMin {
  def apply[A, E <: BufElem[A]](in: Outlet[E], gate: OutI)(implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(gate, stage.in1)
    stage.out
  }

  private final val name = "RunningMin"

  private type Shp[E] = FanInShape2[E, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet [E] (s"$name.in"  ),
      in1 = InI       (s"$name.trig"),
      out = Outlet[E] (s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: RunningValueImpl[_, _] = if (tpe.isDouble) {
        new RunningValueImpl[Double, BufD](name, layer, shape.asInstanceOf[Shp[BufD]], Double.PositiveInfinity )(math.min)
      } else if (tpe.isInt) {
        new RunningValueImpl[Int   , BufI](name, layer, shape.asInstanceOf[Shp[BufI]], Int   .MaxValue         )(math.min)
      } else {
        assert (tpe.isLong)
        new RunningValueImpl[Long  , BufL](name, layer, shape.asInstanceOf[Shp[BufL]], Long  .MaxValue         )(math.min)
      }
      res.asInstanceOf[RunningValueImpl[A, E]]
    }
  }
}