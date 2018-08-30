/*
 *  NormalizeWindow.scala
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
import de.sciss.fscape.stream.impl.{FilterIn3DImpl, FilterLogicImpl, NodeImpl, StageImpl, WindowedLogicImpl}
import graph.NormalizeWindow._

import scala.annotation.switch

object NormalizeWindow {
  def apply(in: OutD, size: OutI, mode: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    b.connect(mode, stage.in2)

    stage.out
  }

  private final val name = "NormalizeWindow"

  private type Shape = FanInShape3[BufD, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape3(
      in0 = InD (s"$name.in"    ),
      in1 = InI (s"$name.size"  ),
      in2 = InI (s"$name.mode"  ),
      out = OutD(s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn3DImpl[BufD, BufI, BufI] {

    private[this] var winBuf    : Array[Double] = _
    private[this] var winSize   : Int = _
    private[this] var mode      : Int = _

    protected def startNextWindow(inOff: Int): Long = {
      if (bufIn1 != null && inOff < bufIn1.size) {
        val oldSize   = winSize
        val _winSize  = math.max(1, bufIn1.buf(inOff))
        if (_winSize != oldSize) {
          winBuf  = new Array[Double](_winSize)
          winSize = _winSize
        }
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        mode = math.max(0, math.min(ModeMax, bufIn2.buf(inOff)))
      }
      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff.toInt, chunk)

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit =
      Util.copy(winBuf, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)

    protected def processWindow(writeToWinOff: Long): Long = {
      val n = writeToWinOff.toInt
      if (n < winSize) {
        Util.clear(winBuf, n, winSize - n)
      }
      if (n > 0) (mode: @switch) match {
        case Normalize    => processNormalize(n)
        case FitUnipolar  => processFitRange(n, lo =  0.0, hi =  1.0)
        case FitBipolar   => processFitRange(n, lo = -1.0, hi = +1.0)
        case ZeroMean     => processZeroMean(n)
      }

      writeToWinOff
    }

    private def processNormalize(n: Int): Unit = {
      val b   = winBuf
      var max = Double.NegativeInfinity
      var i   = 0
      while (i < n) {
        val x = math.abs(b(i))
        if (x > max) max = x
        i += 1
      }
      if (max > 0) {
        val mul = 1.0 / max
        i = 0
        while (i < n) {
          b(i) *= mul
          i += 1
        }
      }
    }

    private def processFitRange(n: Int, lo: Double, hi: Double): Unit = {
      val b   = winBuf
      var min = Double.PositiveInfinity
      var max = Double.NegativeInfinity
      var i   = 0
      while (i < n) {
        val x = b(i)
        if (x < min) min = x
        if (x > max) max = x
        i += 1
      }
      val add = -min
      val mul = if (min < max) (hi - lo) / (max - min) else 1.0
      i = 0
      while (i < n) {
        b(i) = ((b(i) + add) * mul) + lo
        i += 1
      }
    }

    private def processZeroMean(n: Int): Unit = {
      val b   = winBuf
      var sum = 0.0
      var i   = 0
      while (i < n) {
        val x = b(i)
        sum += x
        i += 1
      }
      val add = -sum / n
      i = 0
      while (i < n) {
        b(i) += add
        i += 1
      }
    }
  }
}