/*
 *  ReverseWindow.scala
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

import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.{FilterIn3DImpl, FilterLogicImpl, StageImpl, NodeImpl, WindowedLogicImpl}

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

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape3(
      in0 = InD (s"$name.in"   ),
      in1 = InI (s"$name.size" ),
      in2 = InI (s"$name.clump"),
      out = OutD(s"$name.out"  )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn3DImpl[BufD, BufI, BufI] {

    private[this] var winBuf : Array[Double] = _
    private[this] var winSize: Int = _
    private[this] var clump  : Int = _

    protected def startNextWindow(inOff: Int): Long = {
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

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff.toInt, chunk)

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit =
      Util.copy(winBuf, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)

    protected def processWindow(writeToWinOff: Long): Long = {
      val writeOffI = writeToWinOff.toInt
      var i   = 0
      val cl  = clump
      val cl2 = cl + cl
      var j   = writeOffI - cl  // should use `winBuf.size` instead (flush)?
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