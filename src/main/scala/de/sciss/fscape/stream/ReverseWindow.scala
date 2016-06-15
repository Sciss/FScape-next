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

import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.impl.{FilterIn3Impl, FilterLogicImpl, Out1LogicImpl, StageLogicImpl, WindowedLogicImpl}

/** Reverses contents of windowed input. */
object ReverseWindow {
  /**
    * @param in     the signal to window
    * @param size   the window size. this is clipped to be `&lt;= 1`
    * @param clump  clump size within each window. With a clump size of one,
    *               each window is reversed sample by sample, if the clump size
    *               is two, the first two samples are flipped with the last
    *               two samples, then the third and forth are flipped with the
    *               third and forth before last, etc. Like `size`, `clump` is
    *               sampled at each beginning of a new window and held constant
    *               during the window.
    */
  def apply(in: OutD, size: OutI, clump: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(size  , stage.in1)
    b.connect(clump , stage.in2)
    stage.out
  }

  private final val name = "ReverseWindow"

  private type Shape = FanInShape3[BufD, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends GraphStage[Shape] {
    override def toString = s"$name@${hashCode.toHexString}"

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
      with WindowedLogicImpl[BufD, Shape]
      with FilterLogicImpl  [BufD, Shape]
      with Out1LogicImpl    [BufD, Shape]
      with FilterIn3Impl[BufD, BufI, BufI, BufD] {

    protected val in0: InD = shape.in0

    protected def allocOutBuf0(): BufD = ctrl.borrowBufD()

    private[this] var winBuf : Array[Double] = _
    private[this] var winSize: Int = _
    private[this] var clump  : Int = _

    protected def startNextWindow(inOff: Int): Int = {
      val oldSize = winSize
      if (bufIn1 != null && inOff < bufIn1.size) {
        winSize = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        clump = math.max(1, bufIn2.buf(inOff))
      }
      if (winSize != oldSize) {
        winBuf = new Array[Double](winSize)
      }
      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff, chunk)

    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit =
      Util.copy(winBuf, readFromWinOff, bufOut0.buf, outOff, chunk)

    protected def processWindow(writeToWinOff: Int, flush: Boolean): Int = {
      var i   = 0
      val cl  = clump
      val cl2 = cl + cl
      var j   = writeToWinOff - cl
      val b   = winBuf
      while (i < j) {
        val k = i + cl
        while (i < k) {
          val tmp = b(i)
          b(i) = b(j)
          b(j) = tmp
          i += 1
          j += 1
        }
        j -= cl2
      }
      writeToWinOff
    }
  }
}