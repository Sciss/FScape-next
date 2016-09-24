/*
 *  MelFilter.scala
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

import akka.stream.{Attributes, FanInShape6}
import de.sciss.fscape.stream.impl.{FilterIn6DImpl, FilterLogicImpl, NodeImpl, StageImpl, WindowedLogicImpl}

object MelFilter {
  def apply(in: OutD, size: OutI, minFreq: OutD, maxFreq: OutD, sampleRate: OutD, bands: OutI)
           (implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(size      , stage.in1)
    b.connect(minFreq   , stage.in2)
    b.connect(maxFreq   , stage.in3)
    b.connect(sampleRate, stage.in4)
    b.connect(bands     , stage.in5)
    stage.out
  }

  private final val name = "MelFilter"

  private type Shape = FanInShape6[BufD, BufI, BufD, BufD, BufD, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape6(
      in0 = InD (s"$name.in"        ),
      in1 = InI (s"$name.size"      ),
      in2 = InD (s"$name.minFreq"   ),
      in3 = InD (s"$name.maxFreq"   ),
      in4 = InD (s"$name.sampleRate"),
      in5 = InI (s"$name.bands"     ),
      out = OutD(s"$name.out"       )
    )
    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with FilterLogicImpl[BufD, Shape]
      with WindowedLogicImpl[Shape]
      with FilterIn6DImpl[BufD, BufI, BufD, BufD, BufD, BufI] {

    private[this] var winSize = 0
    private[this] var winBuf  : Array[Double] = _

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf = null
    }

    protected def startNextWindow(inOff: Int): Int = {
      val oldSize = winSize
      if (bufIn1 != null && inOff < bufIn1.size) {
        winSize = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        ??? // minFreq = bufIn2.buf(inOff)
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        ??? // maxFreq = bufIn3.buf(inOff)
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        ??? // sampleRate = bufIn4.buf(inOff)
      }
      if (bufIn5 != null && inOff < bufIn5.size) {
        ??? // bands = bufIn5.buf(inOff)
      }
      if (winSize != oldSize) {
        winBuf = new Array[Double](winSize)
      }
      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit = {
      ???
//      val cy    = coef
//      val cx    = 1.0 - math.abs(cy)
//      val a     = bufIn0.buf
//      val b     = winBuf
//      var ai    = inOff
//      var bi    = writeToWinOff
//      val stop  = ai + chunk
//      while (ai < stop) {
//        val x0 = a(ai)
//        val y0 = b(bi)
//        val y1 = (cx * x0) + (cy * y0)
//        b(bi)  = y1
//        ai += 1
//        bi += 1
//      }
    }

    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit =
      ??? // Util.copy(winBuf, readFromWinOff, bufOut0.buf, outOff, chunk)

    protected def processWindow(writeToWinOff: Int): Int = ??? // writeToWinOff
  }
}