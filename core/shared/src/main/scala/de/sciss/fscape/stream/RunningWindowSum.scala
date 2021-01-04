/*
 *  RunningWindowSum.scala
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

import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{NodeImpl, RunningWindowValueLogic, StageImpl}

object RunningWindowSum {
  def apply[A, E <: BufElem[A]](in: Outlet[E], size: OutI, gate: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    b.connect(gate, stage.in2)
    stage.out
  }

  private final val name = "RunningWindowSum"

  private type Shp[E] = FanInShape3[E, BufI, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape3(
      in0 = Inlet [E] (s"$name.in"  ),
      in1 = InI       (s"$name.size"),
      in2 = InI       (s"$name.gate"),
      out = Outlet[E] (s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: RunningWindowValueLogic[_, _] = if (tpe.isDouble) {
        new RunningWindowValueLogic[Double, BufD](name, layer, shape.asInstanceOf[Shp[BufD]])(_ + _)
      } else if (tpe.isInt) {
        new RunningWindowValueLogic[Int   , BufI](name, layer, shape.asInstanceOf[Shp[BufI]])(_ + _)
      } else {
        assert (tpe.isLong)
        new RunningWindowValueLogic[Long  , BufL](name, layer, shape.asInstanceOf[Shp[BufL]])(_ + _)
      }
      res.asInstanceOf[RunningWindowValueLogic[A, E]]
    }
  }
}