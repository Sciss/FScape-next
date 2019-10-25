/*
 *  ARCWindow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape5}
import de.sciss.fscape.stream.impl.{DemandFilterIn5D, DemandFilterLogic, DemandWindowedLogicOLD, NodeImpl, StageImpl}

object ARCWindow {
  def apply(in: OutD, size: OutI, lo: OutD, hi: OutD, lag: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    b.connect(lo  , stage.in2)
    b.connect(hi  , stage.in3)
    b.connect(lag , stage.in4)
    stage.out
  }

  private final val name = "ARCWindow"

  private type Shape = FanInShape5[BufD, BufI, BufD, BufD, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape5(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.size"),
      in2 = InD (s"$name.lo"  ),
      in3 = InD (s"$name.hi"  ),
      in4 = InD (s"$name.lag" ),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with DemandFilterLogic[BufD, Shape]
      with DemandWindowedLogicOLD[Shape]
      with DemandFilterIn5D[BufD, BufI, BufD, BufD, BufD] {

    private[this] var winSize = 0
    private[this] var lo      = 0.0
    private[this] var hi      = 0.0
    private[this] var lag     = 0.0
    private[this] var winBuf  : Array[Double] = _

    private[this] var init    = true
    private[this] var minMem  = 0.0
    private[this] var maxMem  = 0.0
    private[this] var mul     = 0.0
    private[this] var add     = 0.0

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf = null
    }

    protected def startNextWindow(): Long = {
      val oldSize = winSize
      val inOff   = auxInOff
      if (bufIn1 != null && inOff < bufIn1.size) {
        winSize = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        lo = bufIn2.buf(inOff)
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        hi = bufIn3.buf(inOff)
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        lag = bufIn4.buf(inOff)
      }
      if (winSize != oldSize) {
        winBuf = new Array[Double](winSize)
      }
      winSize
    }

    protected def canStartNextWindow: Boolean = auxInRemain > 0 || (auxInValid && {
      isClosed(in1) && isClosed(in2) && isClosed(in3) && isClosed(in4)
    })

    protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, mainInOff, winBuf, writeToWinOff.toInt, chunk)

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      val _add  = add
      val _mul  = mul
      val a     = winBuf
      val b     = bufOut0.buf
      var ai    = readFromWinOff.toInt
      var bi    = outOff
      val stop  = ai + chunk
      while (ai < stop) {
        val x0 = a(ai)
        val y1 = x0 * _mul + _add
        b(bi)  = y1
        ai += 1
        bi += 1
      }
    }

    protected def processWindow(writeToWinOff: Long): Long = {
      val writeOffI = writeToWinOff.toInt
      if (writeOffI == 0) return writeToWinOff

      val b         = winBuf
      var min       = b(0)
      var max       = b(0)
      var i         = 1
      while (i < writeOffI) {
        val x = b(i)
        if      (x > max) max = x
        else if (x < min) min = x
        i += 1
      }

      if (init) {
        init = false
        minMem = min
        maxMem = max
      } else {
        val cy = lag
        val cx = 1.0 - math.abs(cy)
        minMem = minMem * cy + min * cx
        maxMem = maxMem * cy + max * cx
      }

      // linlin := (in - min) / (max - min) * (hi - lo) + lo
      // mul := (hi - lo) / (max - min)
      // linlin := (in - min) * mul + lo
      // linlin := (in - min + lo/mul) * mul
      // add := lo/mul - min
      // linlin := (in + add) * mul
      // linlin := in * mul + add * mul
      // add2 := lo - min * mul
      // linlin := in * mul + add2

      if (minMem == maxMem) {
        mul = 0
        add = lo
      } else {
        mul = (hi - lo) / (maxMem - minMem)
        add = lo - minMem * mul
      }

      writeToWinOff
    }
  }
}