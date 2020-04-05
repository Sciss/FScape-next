/*
 *  RotateFlipMatrix.scala
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

import akka.stream.{Attributes, FanInShape4}
import de.sciss.fscape.graph.RotateFlipMatrix._
import de.sciss.fscape.stream.impl.deprecated.{FilterIn4DImpl, FilterLogicImpl, WindowedLogicImpl}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

import scala.annotation.switch

object RotateFlipMatrix {
  def apply(in: OutD, rows: OutI, columns: OutI, mode: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in     , stage.in0)
    b.connect(rows   , stage.in1)
    b.connect(columns, stage.in2)
    b.connect(mode   , stage.in3)
    stage.out
  }

  private final val name = "RotateFlipMatrix"

  private type Shp = FanInShape4[BufD, BufI, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape4(
      in0 = InD (s"$name.in"     ),
      in1 = InI (s"$name.rows"   ),
      in2 = InI (s"$name.columns"),
      in3 = InI (s"$name.mode"   ),
      out = OutD(s"$name.out"    )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final val Transpose = 16

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with WindowedLogicImpl[Shp]
      with FilterLogicImpl[BufD, Shp]
      with FilterIn4DImpl[BufD, BufI, BufI, BufI] {

    private[this] var inBuf  : Array[Double] = _
    private[this] var outBuf : Array[Double] = _
    private[this] var rows   : Int = _
    private[this] var columns: Int = _
    private[this] var mode   : Int = _
    private[this] var needsDoubleBuf: Boolean = _
    private[this] var winSize: Int = _

    protected def startNextWindow(inOff: Int): Long = {
      val oldSize = winSize
      val oldMode = mode
      if (bufIn1 != null && inOff < bufIn1.size) {
        rows = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        columns = math.max(1, bufIn2.buf(inOff))
      }
      val isSquare = rows == columns
      if (bufIn3 != null && inOff < bufIn3.size) {
        var _mode = math.max(0, math.min(11, bufIn3.buf(inOff)))
        // logically remove cw + ccw here
        if ((_mode & 12) == 12) _mode &= ~12
        // reduce number of steps
        if (isSquare) {
          if      (_mode == (FlipX | Rot90CCW) || _mode == (FlipY | Rot90CW)) _mode = Transpose
          else if (_mode == (FlipY | Rot90CCW) || _mode == (FlipX | Rot90CW)) _mode = Transpose | Rot180
        }

        mode = _mode
      }
      needsDoubleBuf  = !isSquare && (mode & 12) != 0
      winSize         = rows * columns
      if (winSize != oldSize) {
        inBuf  = new Array[Double](winSize)
        if (needsDoubleBuf) {
          outBuf  = new Array[Double](winSize)
        } else{
          outBuf  = inBuf
        }
      } else if (mode != oldMode) {
        if (needsDoubleBuf) {
          if (outBuf eq inBuf) outBuf = new Array[Double](winSize)
        } else {
          outBuf = inBuf
        }
      }
      winSize
    }

    override protected def stopped(): Unit = {
      super.stopped()
      inBuf   = null
      outBuf  = null
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, inBuf, writeToWinOff.toInt, chunk)

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit =
      Util.copy(outBuf, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)

    // in-place
    private def flipX(): Unit = {
      val a     = inBuf
      val _cols = columns
      var off   = 0
      val stop  = winSize
      while (off < stop) {
        var i = off
        off += _cols
        var j = off - 1
        while (i < j) {
          val tmp = a(i)
          a(i)    = a(j)
          a(j)    = tmp
          i += 1
          j -= 1
        }
      }
    }

    // in-place
    private def flipY(): Unit = {
      val a     = inBuf
      val _cols = columns
      var i     = 0
      var j     = winSize - _cols
      while (i < j) {
        val iNext = i + _cols
        val jNext = j - _cols
        while (i < iNext) {
          val tmp = a(i)
          a(i)    = a(j)
          a(j)    = tmp
          i += 1
          j += 1
        }
        j = jNext
      }
    }

    // in-place
    private def rot180(): Unit =
      Util.reverse(inBuf, 0, winSize)

    // in-place
    private def transpose(): Unit = {
      val a = inBuf
      val n = columns
      val jStep = n + 1
      var m = n
      var i = 0
      var j = 0
      val stop = winSize
      while (i < stop) {
        i = j
        val iNext = i + m
        val jNext = j + jStep
        while (i < iNext) {
          val tmp = a(i)
          a(i) = a(j)
          a(j) = tmp
          i += 1
          j += n
        }
        m -= 1
        j = jNext
      }
    }

    // in-place
    private def sqrRot90CW(): Unit = {
      transpose()
      flipX()
    }

    // in-place
    private def sqrRot90CCW(): Unit = {
      transpose()
      flipY()
    }

    private def rot90CW(): Unit = {
      val a     = inBuf
      val b     = outBuf
      val _cols = columns
      val _rows = rows
      var i     = 0
      var j     = _cols * (_rows - 1)
      val stop  = winSize
      while (i < stop) {
        val iNext = i + _rows
        val jNext = j + 1
        while (i < iNext) {
          b(i) = a(j)
          i += 1
          j -= _cols
        }
        j = jNext
      }
    }

    private def rot90CCW(): Unit = {
      val a     = inBuf
      val b     = outBuf
      val _cols = columns
      val _rows = rows
      var i     = 0
      var j     = _rows * (_cols - 1)
      val stop  = winSize
      while (i < stop) {
        val iNext = i + _cols
        val jNext = j + 1
        while (i < iNext) {
          b(j) = a(i)
          i += 1
          j -= _rows
        }
        j = jNext
      }
    }

    protected def processWindow(writeToWinOff: Long): Long = {
      val size = winSize
      if (writeToWinOff < size) {
        val writeOffI = writeToWinOff.toInt
        Util.clear(inBuf, writeOffI, size - writeOffI)
      }

      val _mode = mode
      (_mode & 3: @switch) match {
        case Through  =>
        case FlipX    => flipX()
        case FlipY    => flipY()
        case Rot180   => rot180()
      }
      (_mode >> 2: @switch) match {
        case 0 =>
        case 1 => if (needsDoubleBuf) rot90CW()  else sqrRot90CW()
        case 2 => if (needsDoubleBuf) rot90CCW() else sqrRot90CCW()
        case 4 => transpose()
      }

      size
    }
  }
}