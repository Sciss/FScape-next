/*
 *  RepeatWindow.scala
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
import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.{FilterIn3DImpl, FilterLogicImpl, StageImpl, StageLogicImpl, WindowedLogicImpl}

/** Repeats contents of windowed input.
  * XXX TODO --- implementation not very fast for `size == 1`
  */
object RepeatWindow {
  /**
    * @param in     the signal to window
    * @param size   the window size. this is clipped to be `&lt;= 1`
    * @param num    the number of times each window is repeated
    */
  def apply(in: OutD, size: OutI, num: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    b.connect(num , stage.in2)
    stage.out
  }

  private final val name = "RepeatWindow"

  private type Shape = FanInShape3[BufD, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape3(
      in0 = InD (s"$name.in"   ),
      in1 = InI (s"$name.size" ),
      in2 = InI (s"$name.clump"),
      out = OutD(s"$name.out"  )
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn3DImpl[BufD, BufI, BufI] {

    private[this] var winBuf : Array[Double] = _
    private[this] var winSize: Int = _
    private[this] var num    : Int = _

    protected def startNextWindow(inOff: Int): Int = {
      val oldSize = winSize
      if (bufIn1 != null && inOff < bufIn1.size) {
        winSize = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        num = math.max(1, bufIn2.buf(inOff))  // XXX TODO -- we could permit zero
      }
      if (winSize != oldSize) {
        winBuf = new Array[Double](winSize)
      }
      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff, chunk)

    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit = {
      var inOff0  = readFromWinOff % winSize
      var remain  = chunk
      var outOff0 = outOff
      while (remain > 0) {
        val chunk0 = math.min(winSize - inOff0, remain)
        Util.copy(winBuf, inOff0, bufOut0.buf, outOff0, chunk0)
        inOff0   = (inOff0 + chunk0) % winSize
        outOff0 += chunk0
        remain  -= chunk0
      }
    }

    protected def processWindow(writeToWinOff: Int): Int = winSize * (num - 1) + writeToWinOff
  }
}