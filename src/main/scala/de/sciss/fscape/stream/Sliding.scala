/*
 *  Sliding.scala
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

package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import akka.stream.scaladsl.GraphDSL
import akka.stream.stage.{GraphStage, GraphStageLogic}
import de.sciss.fscape.stream.impl.FilterIn3Impl

object Sliding {
  def apply(in: Outlet[BufD], size: Outlet[BufI], step: Outlet[BufI])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] = {
    val stage0  = new Impl(ctrl)
    val stage   = b.add(stage0)
    import GraphDSL.Implicits._
    in   ~> stage.in0
    size ~> stage.in1
    step ~> stage.in2

    stage.out
  }

  private final class Impl(ctrl: Control) extends GraphStage[FanInShape3[BufD, BufI, BufI, BufD]] {
    val shape = new FanInShape3(
      in0 = Inlet [BufD]("Sliding.in"  ),
      in1 = Inlet [BufI]("Sliding.size"),
      in2 = Inlet [BufI]("Sliding.step"),
      out = Outlet[BufD]("Sliding.out" )
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(shape, ctrl)

    private final class Logic(protected val shape: FanInShape3[BufD, BufI, BufI, BufD],
                              protected val ctrl: Control)
      extends GraphStageLogic(shape) with FilterIn3Impl[BufD, BufI, BufI, BufD] {

      protected def process(): Unit = {
        ???
      }
    }
  }
}