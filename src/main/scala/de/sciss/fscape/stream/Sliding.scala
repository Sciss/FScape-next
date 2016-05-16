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

import scala.annotation.tailrec

/** Sliding overlapping window. */
object Sliding {
  /**
    * @param in     the signal to window
    * @param size   the window size. this is clipped to be `&lt;= 1`
    * @param step   the step size. this is clipped to be `&lt;= 1` and `&lt;= size`
    */
  def apply(in: Outlet[BufD], size: Outlet[BufI], step: Outlet[BufI])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] = {
    val stage0  = new Stage(ctrl)
    val stage   = b.add(stage0)
    import GraphDSL.Implicits._
    in   ~> stage.in0
    size ~> stage.in1
    step ~> stage.in2

    stage.out
  }

  private final class Window(val buf: Array[Double], var off: Int)

  private final class Stage(ctrl: Control) extends GraphStage[FanInShape3[BufD, BufI, BufI, BufD]] {
    val shape = new FanInShape3(
      in0 = Inlet [BufD]("Sliding.in"  ),
      in1 = Inlet [BufI]("Sliding.size"),
      in2 = Inlet [BufI]("Sliding.step"),
      out = Outlet[BufD]("Sliding.out" )
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(shape, ctrl)
  }

  private final class Logic(protected val shape: FanInShape3[BufD, BufI, BufI, BufD],
                            protected val ctrl: Control)
    extends GraphStageLogic(shape) with FilterIn3Impl[BufD, BufI, BufI, BufD] {

    private[this] var inOff       = 0  // regarding `bufIn`
    private[this] var inRemain    = 0
    private[this] var stepRemain  = 0

    private[this] var size  : Int  = _
    private[this] var step  : Int  = _

    @inline
    private[this] def isNextStep = stepRemain == 0 && bufIn0 != null

    @tailrec
    protected def process(): Unit = {
      var stateChange = false

      if (canRead) {
        readIns()
        inOff     = 0
        inRemain  = bufIn0.size
      }
      if (isNextStep) {
        if (bufIn1 != null && inOff < bufIn1.size) {
          size = math.max(1, bufIn1.buf(inOff))
        }
        if (bufIn2 != null && inOff < bufIn2.size) {
          step = math.max(1, math.min(size, bufIn2.buf(inOff)))
        }
        stepRemain = step
        val win = new Window(new Array[Double](size), off = 0)
        ???
      }

      if (stateChange) process()
    }
  }
}