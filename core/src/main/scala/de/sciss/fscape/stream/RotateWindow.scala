/*
 *  RotateWindow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.{FilterIn3DImpl, FilterLogicImpl, NodeImpl, StageImpl, WindowedLogicImpl}
import de.sciss.numbers.IntFunctions

object RotateWindow {
  def apply(in: OutD, size: OutI, amount: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(size  , stage.in1)
    b.connect(amount, stage.in2)

    stage.out
  }

  private final val name = "RotateWindow"

  private type Shape = FanInShape3[BufD, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape3(
      in0 = InD (s"$name.in"    ),
      in1 = InI (s"$name.size"  ),
      in2 = InI (s"$name.amount"),
      out = OutD(s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn3DImpl[BufD, BufI, BufI] {

    private[this] var winBuf    : Array[Double] = _
    private[this] var winSize   : Int = _
    private[this] var amountInv : Int = _

    protected def startNextWindow(inOff: Int): Long = {
      if (bufIn1 != null && inOff < bufIn1.size) {
        val oldSize   = winSize
        val _winSize  = math.max(1, bufIn1.buf(inOff))
        val amount    = oldSize - amountInv
        amountInv     = _winSize - amount
        if (_winSize != oldSize) {
          winBuf  = new Array[Double](_winSize)
          winSize = _winSize
        }
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        val amount  = IntFunctions.mod(bufIn2.buf(inOff), winSize)
        amountInv   = winSize - amount
      }
      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff.toInt, chunk)

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      val _winSize  = winSize
      val n         = (readFromWinOff.toInt + amountInv) % _winSize
      val m         = math.min(chunk, _winSize - n)
      Util.copy(winBuf, n, bufOut0.buf, outOff, m)
      val p         = chunk - m
      if (p > 0) {
        Util.copy(winBuf, 0, bufOut0.buf, outOff + m, p)
      }
    }

    protected def processWindow(writeToWinOff: Long): Long = {
      val n = writeToWinOff.toInt
      if (n < winSize) {
        Util.clear(winBuf, n, winSize - n)
      }
      writeToWinOff
    }
  }
}