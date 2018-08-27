/*
 *  Viterbi.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape4, Outlet}
import de.sciss.fscape.stream.impl.{NodeImpl, Out1IntImpl, Out1LogicImpl, StageImpl}

object Viterbi {
  def apply(mul: OutD, add: OutD, numStates: OutI, numFrames: OutI)(implicit b: Builder): OutI = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(mul       , stage.in0)
    b.connect(add       , stage.in1)
    b.connect(numStates , stage.in2)
    b.connect(numFrames , stage.in3)
    stage.out
  }

  private final val name = "Viterbi"

  private type Shape = FanInShape4[BufD, BufD, BufI, BufI, BufI]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape4(
      in0 = InD (s"$name.mul"       ),
      in1 = InD (s"$name.add"       ),
      in2 = InI (s"$name.numStates" ),
      in3 = InI (s"$name.numFrames" ),
      out = OutI(s"$name.out"       )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with Out1IntImpl[Shape] with Out1LogicImpl[BufI, Shape] {

    protected var bufOut0: BufI = _

    protected def out0: Outlet[BufI] = shape.out

    def inValid: Boolean = ???

    ??? // install handlers

    protected def freeOutputBuffers(): Unit = ???

    def process(): Unit = ???
  }
}
