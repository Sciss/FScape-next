/*
 *  ReverseWindow.scala
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
import akka.stream.scaladsl.GraphDSL
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.impl.{FilterIn2Impl, WindowedLogicImpl}

/** Reverses contents of windowed input. */
object ReverseWindow {
  /**
    * @param in     the signal to window
    * @param size   the window size. this is clipped to be `&lt;= 1`
    */
  def apply(in: Outlet[BufD], size: Outlet[BufI])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] = {
    val stage0  = new Stage(ctrl)
    val stage   = b.add(stage0)
    import GraphDSL.Implicits._
    in   ~> stage.in0
    size ~> stage.in1

    stage.out
  }

  private final class Stage(ctrl: Control) extends GraphStage[FanInShape2[BufD, BufI, BufD]] {
    val shape = new FanInShape2(
      in0 = Inlet [BufD]("ReverseWindow.in"  ),
      in1 = Inlet [BufI]("ReverseWindow.size"),
      out = Outlet[BufD]("ReverseWindow.out" )
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(shape, ctrl)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(protected val shape: FanInShape2[BufD, BufI, BufD],
                            protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with WindowedLogicImpl[BufD, BufD, FanInShape2[BufD, BufI, BufD]]
      with FilterIn2Impl                            [BufD, BufI, BufD] {

    protected val in0: Inlet[BufD] = shape.in0

    protected def allocOutBuf(): BufD = ctrl.borrowBufD()

    private[this] var winBuf : Array[Double] = _
    private[this] var winSize: Int = _

    protected def startNextWindow(inOff: Int): Int = {
      val oldSize = winSize
      if (bufIn1 != null && inOff < bufIn1.size) {
        winSize = math.max(1, bufIn1.buf(inOff))
      }
      if (winSize != oldSize) {
        winBuf = new Array[Double](winSize)
      }
      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff, chunk)

    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit =
      Util.copy(winBuf, readFromWinOff, bufOut.buf, outOff, chunk)

    protected def processWindow(writeToWinOff: Int): Int = {
      var i = 0
      var j = writeToWinOff - 1
      val b = winBuf
      while (i < j) {
        val tmp = b(i)
        b(i) = b(j)
        b(j) = tmp
        i += 1
        j -= 1
      }
      writeToWinOff
    }
  }
}