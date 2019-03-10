/*
 *  Pearson.scala
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

import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.{FilterIn3DImpl, FilterLogicImpl, NodeImpl, StageImpl, WindowedLogicImpl}

object Pearson {
  def apply(x: OutD, y: OutD, size: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(x   , stage.in0)
    b.connect(y   , stage.in1)
    b.connect(size, stage.in2)
    stage.out
  }

  private final val name = "Pearson"

  private type Shape = FanInShape3[BufD, BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape3(
      in0 = InD (s"$name.x"   ),
      in1 = InD (s"$name.y"   ),
      in2 = InI (s"$name.size"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn3DImpl[BufD, BufD, BufI] {

    private[this] var xBuf: Array[Double] = _
    private[this] var yBuf: Array[Double] = _
    private[this] var size: Int = _
    private[this] var coef: Double  = _

    override protected def stopped(): Unit = {
      super.stopped()
      xBuf = null
      yBuf = null
    }

    protected def startNextWindow(inOff: Int): Long = {
      val oldSize = size
      if (bufIn2 != null && inOff < bufIn2.size) {
        size = math.max(1, bufIn2.buf(inOff))
      }
      if (size != oldSize) {
        xBuf = new Array[Double](size)
        yBuf = new Array[Double](size)
      }
      size
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit = {
      val writeOffI = writeToWinOff.toInt
      Util.copy(bufIn0.buf, inOff, xBuf, writeOffI, chunk)
      val _in1 = bufIn1
      if (_in1 != null) {
        val stop    = math.min(_in1.size, inOff + chunk)
        val chunk1  = math.max(0, stop - inOff)
        if (chunk1 > 0)
          Util.copy(_in1.buf, inOff, yBuf, writeOffI, chunk1)
        val chunk2  = chunk - chunk1
        if (chunk2 > 0) {
          Util.clear(yBuf, writeOffI + chunk1, chunk2)
        }
      } else {
        Util.clear(yBuf, writeOffI, chunk)
      }
    }

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      require(chunk <= 1 && readFromWinOff == 0)
      if (chunk == 1) {
        bufOut0.buf(outOff) = coef
      }
    }

    protected def processWindow(lenL: Long): Long = {
      val _x  = xBuf
      val _y  = yBuf
      var xm  = 0.0
      var ym  = 0.0
      var i   = 0
      val len = lenL.toInt
      while (i < len) {
        xm += _x(i)
        ym += _y(i)
        i  += 1
      }
      xm /= len   // now xm = mean(x)
      ym /= len   // now ym = mean(y)

      var xsq = 0.0
      var ysq = 0.0
      var sum = 0.0
      i = 0
      while (i < len) {
        val xd = _x(i) - xm
        val yd = _y(i) - ym
        xsq += xd * xd
        ysq += yd * yd
        sum += xd * yd
        i  += 1
      }
      // now xsq = variance(x), ysq = variance(y)
      val denom = math.sqrt(xsq * ysq)
      coef = if (denom > 0) sum / denom else sum
      1
    }
  }
}