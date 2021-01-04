/*
 *  ArithmSeq.scala
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
import de.sciss.fscape.stream.impl.{NodeImpl, SeqGenLogicD, SeqGenLogicI, SeqGenLogicL, StageImpl}

object ArithmSeq {
  def apply[A, E <: BufElem[A]](start: Outlet[E], step: Outlet[E], length: OutL)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(start , stage.in0)
    b.connect(step  , stage.in1)
    b.connect(length, stage.in2)
    stage.out
  }

  private final val name = "ArithmSeq"

  private type Shp[E] = FanInShape3[E, E, BufL, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape = new FanInShape3(
      in0 = Inlet[E] (s"$name.start" ),
      in1 = Inlet[E] (s"$name.step"  ),
      in2 = InL      (s"$name.length"),
      out = Outlet[E](s"$name.out"   )
    )

    // handle specialization
    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: NodeImpl[_] = if (tpe.isInt) {
        new SeqGenLogicI(name, shape.asInstanceOf[Shp[BufI]], layer)((a, b) => a + b)
      } else if (tpe.isLong) {
        new SeqGenLogicL(name, shape.asInstanceOf[Shp[BufL]], layer)((a, b) => a + b)
      } else /*if (tpe.isDouble)*/ {
        assert (tpe.isDouble)
        new SeqGenLogicD(name, shape.asInstanceOf[Shp[BufD]], layer)((a, b) => a + b)
      }

      res.asInstanceOf[NodeImpl[Shape]]
    }
  }
}