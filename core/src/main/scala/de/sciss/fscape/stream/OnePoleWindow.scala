/*
 *  OnePoleWindow.scala
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

import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.{FilterIn3DImpl, FilterLogicImpl, StageImpl, NodeImpl, WindowedLogicImpl}

object OnePoleWindow {
  def apply(in: OutD, size: OutI, coef: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    b.connect(coef, stage.in2)
    stage.out
  }

  private final val name = "OnePoleWindow"

  private type Shape = FanInShape3[BufD, BufI, BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape3(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.size"),
      in2 = InD (s"$name.coef"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with FilterLogicImpl[BufD, Shape]
      with WindowedLogicImpl[Shape]
      with FilterIn3DImpl[BufD, BufI, BufD] {

    private[this] var winSize = 0
    private[this] var coef    = 0.0
    private[this] var winBuf  : Array[Double] = _
    
    override protected def stopped(): Unit = {
      super.stopped()
      winBuf = null
    }

    protected def startNextWindow(inOff: Int): Long = {
      val oldSize = winSize
      if (bufIn1 != null && inOff < bufIn1.size) {
        winSize = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        coef = bufIn2.buf(inOff)
      }
      if (winSize != oldSize) {
        winBuf = new Array[Double](winSize)
      }
      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit = {
      val cy    = coef
      val cx    = 1.0 - math.abs(cy)
      val a     = bufIn0.buf
      val b     = winBuf
      var ai    = inOff
      var bi    = writeToWinOff.toInt
      val stop  = ai + chunk
      while (ai < stop) {
        val x0 = a(ai)
        val y0 = b(bi)
        val y1 = (cx * x0) + (cy * y0)
        b(bi)  = y1
        ai += 1
        bi += 1
      }
    }

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit =
      Util.copy(winBuf, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)

    protected def processWindow(writeToWinOff: Long): Long = writeToWinOff
  }
}