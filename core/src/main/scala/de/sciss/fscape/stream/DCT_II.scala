/*
 *  DCT_II.scala
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

import akka.stream.{Attributes, FanInShape4}
import de.sciss.fscape.stream.impl.{FilterIn4DImpl, FilterLogicImpl, NodeImpl, StageImpl, WindowedLogicImpl}

object DCT_II {
  def apply(in: OutD, size: OutI, numCoeffs: OutI, zero: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(size      , stage.in1)
    b.connect(numCoeffs , stage.in2)
    b.connect(zero      , stage.in3)
    stage.out
  }

  private final val name = "DCT_II"

  private type Shape = FanInShape4[BufD, BufI, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape4(
      in0 = InD (s"$name.in"       ),
      in1 = InI (s"$name.size"     ),
      in2 = InI (s"$name.numCoeffs"),
      in3 = InI (s"$name.zero"     ),
      out = OutD(s"$name.out"      )
    )
    def createLogic(attr: Attributes) = new Logic(shape)
  }

  // XXX TODO --- we could store pre-calculated cosine tables for
  // sufficiently small table sizes
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with FilterLogicImpl[BufD, Shape]
      with WindowedLogicImpl[Shape]
      with FilterIn4DImpl[BufD, BufI, BufI, BufI] {

    private[this] var size      = 0
    private[this] var zero      = false
    private[this] var numCoeffs = 0

    private[this] var winBuf  : Array[Double] = _
    private[this] var coefBuf : Array[Double] = _

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf  = null
      coefBuf = null
    }

    protected def startNextWindow(inOff: Int): Long = {
      var updatedBuf = false
      if (bufIn1 != null && inOff < bufIn1.size) {
        val _size = math.max(1, bufIn1.buf(inOff))
        if (size != _size) {
          size     = _size
          updatedBuf = true
        }
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        val _zero = bufIn3.buf(inOff) != 0
        if (zero != _zero) {
          zero        = _zero
          updatedBuf  = true
        }
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        val _numCoeffs = math.max(1, bufIn2.buf(inOff))
        if (numCoeffs != _numCoeffs) {
          numCoeffs    = _numCoeffs
          updatedBuf = true
        }
      }
      if (updatedBuf) {
        winBuf  = new Array[Double](size)
        coefBuf = new Array[Double](numCoeffs)
      }
      size
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff.toInt, chunk)

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit =
      Util.copy(coefBuf, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)

    protected def processWindow(writeToWinOff: Long): Long = {
      val _size       = size
      val _numCoeffs  = numCoeffs
      val _bufIn      = winBuf
      val _bufOut     = coefBuf

      Util.clear(_bufOut, 0, _numCoeffs)
      val r     = math.Pi / _size   // XXX TODO --- divide by `_size` or `_numCoeffs`?
      val nP    = if (zero) 0 else 1
      var n     = 0
      while (n < _numCoeffs) {
        val s = r * (n + nP)
        var i = 0
        while (i < _size) {
          _bufOut(n) += _bufIn(i) * math.cos(s * (i + 0.5))
          i += 1
        }
        n += 1
      }
      _numCoeffs
    }
  }
}