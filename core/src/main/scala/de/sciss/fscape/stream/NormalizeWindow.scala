/*
 *  NormalizeWindow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.graph.NormalizeWindow.{FitBipolar, FitUnipolar, Normalize, ZeroMean}
import de.sciss.fscape.stream.impl.{DemandFilterWindowedLogic, NodeImpl, StageImpl}

import scala.annotation.switch

object NormalizeWindow {
  def apply(in: OutD, size: OutI, mode: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    b.connect(mode, stage.in2)

    stage.out
  }

  private final val name = "NormalizeWindow"

  private type Shape = FanInShape3[BufD, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape3(
      in0 = InD (s"$name.in"    ),
      in1 = InI (s"$name.size"  ),
      in2 = InI (s"$name.mode"  ),
      out = OutD(s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with DemandFilterWindowedLogic[Double, BufD, Shape] {

    private[this] var mode      : Int     = -1
    private[this] var bufModeOff: Int     = 0
    private[this] var bufMode   : BufI    = _
    private[this] var needsMode : Boolean = true

    // constructor
    {
      installMainAndWindowHandlers()
      new _InHandlerImpl(inletMode)(modeValid)
    }

    private def modeValid = mode >= 0

    protected def tpeSignal: StreamType[Double, BufD] = StreamType.double

    protected def inletSignal : Inlet[BufD]   = shape.in0
    protected def inletWinSize: InI           = shape.in1
    protected def inletMode   : InI           = shape.in2
    protected def out0        : Outlet[BufD]  = shape.out

    protected def winParamsValid: Boolean = modeValid
    protected def needsWinParams: Boolean = needsMode

    protected def requestWinParams(): Unit = {
      needsMode = true
    }

    protected def freeWinParamBuffers(): Unit =
      freeModeBuf()

    private def freeModeBuf(): Unit =
      if (bufMode != null) {
        bufMode.release()
        bufMode = null
      }

    protected def tryObtainWinParams(): Boolean =
      if (needsMode && bufMode != null && bufModeOff < bufMode.size) {
        mode       = math.max(0, bufMode.buf(bufModeOff))
        bufModeOff += 1
        needsMode = false
        true
      } else if (isAvailable(inletMode)) {
        freeModeBuf()
        bufMode    = grab(inletMode)
        bufModeOff = 0
        tryPull(inletMode)
        true
      } else if (needsMode && isClosed(inletMode) && modeValid) {
        needsMode = false
        true
      } else {
        false
      }

    //    protected def startNextWindow(inOff: Int): Long = {
//      if (bufIn1 != null && inOff < bufIn1.size) {
//        val oldSize   = winSize
//        val _winSize  = math.max(1, bufIn1.buf(inOff))
//        if (_winSize != oldSize) {
//          winBuf  = new Array[Double](_winSize)
//          winSize = _winSize
//        }
//      }
//      if (bufIn2 != null && inOff < bufIn2.size) {
//        mode = math.max(0, math.min(ModeMax, bufIn2.buf(inOff)))
//      }
//      winSize
//    }

//    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
//      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff.toInt, chunk)
//
//    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit =
//      Util.copy(winBuf, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)

    override protected def prepareWindow(win: Array[Double], winInSize: Int, inSignalDone: Boolean): Long = {
      val n = winInSize // writeToWinOff.toInt
//      if (n < winSize) {
//        Util.clear(winBuf, n, winSize - n)
//      }
      if (n > 0) (mode: @switch) match {
        case Normalize    => processNormalize (win, n)
        case FitUnipolar  => processFitRange  (win, n, lo =  0.0, hi =  1.0)
        case FitBipolar   => processFitRange  (win, n, lo = -1.0, hi = +1.0)
        case ZeroMean     => processZeroMean  (win, n)
      }

      winInSize
    }

    private def processNormalize(b: Array[Double], n: Int): Unit = {
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

    private def processFitRange(b: Array[Double], n: Int, lo: Double, hi: Double): Unit = {
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

    private def processZeroMean(b: Array[Double], n: Int): Unit = {
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