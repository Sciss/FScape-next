/*
 *  RunningSum.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.GraphStageLogic
import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{FilterIn2DImpl, RunningValueImpl, StageImpl, StageLogicImpl}

object RunningSum {
  def apply(in: OutD, trig: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(trig, stage.in1)
    stage.out
  }

  private final val name = "RunningSum"

  private type Shape = FanInShape2[BufD, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.trig"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with RunningValueImpl[Shape]
      with FilterIn2DImpl[BufD, BufI] {

    protected def neutralValue: Double = 0.0

    protected def combine(a: Double, b: Double): Double = a + b
  }
}