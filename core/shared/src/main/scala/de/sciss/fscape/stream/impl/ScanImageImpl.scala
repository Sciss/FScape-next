/*
 *  ScanImageImpl.scala
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
package impl

import de.sciss.numbers.IntFunctions

import scala.math.{abs, max, min}

/** A mixin for scanning matrices with interpolation.
  * Assumes that the UGen provides inputs for width/height
  * and interpolation parameters (zero-crossings for sinc etc.)
  */
trait ScanImageImpl {

  // ---- abstract ----

  protected def bufWidthIn      : BufI
  protected def bufHeightIn     : BufI

  protected def bufWrap         : BufI
  protected def bufRollOff      : BufD
  protected def bufKaiserBeta   : BufD
  protected def bufZeroCrossings: BufI

  // ---- impl ----

  // this is currently fix
  private[this] val fltSmpPerCrossing = 4096

  private[this] var rollOff       = -1.0  // must be negative for init detection
  private[this] var kaiserBeta    = -1.0
  private[this] var zeroCrossings = -1
  private[this] var wrapBounds    = false
  private[this] var sx            = 1.0
  private[this] var sy            = 1.0

  protected final var winBuf    : Array[Double] = _
  private[this] var widthIn     : Int           = _
  private[this] var heightIn    : Int           = _

  private[this] var fltLenH     : Int           = _
  private[this] var fltBuf      : Array[Double] = _
  private[this] var fltBufD     : Array[Double] = _
  private[this] var fltGain     : Double        = _

  private[this] var xFltIncr    : Double        = _
  private[this] var yFltIncr    : Double        = _
  private[this] var xGain       : Double        = _
  private[this] var yGain       : Double        = _

  protected final def freeImageBuffer(): Unit =
    winBuf = null

  /** Changes the scaling factor affecting the sinc interpolation.
    * It is not necessary to call this if the scaling is never
    * changed from the default of 1.0.
    */
  protected final def setScale(sx: Double, sy: Double): Unit = {
    this.sx       = sx
    this.sy       = sy
    val xFactMin1 = if (sx == 0) 1.0 else min(1.0, sx)
    val yFactMin1 = if (sy == 0) 1.0 else min(1.0, sy)
    // for the malformed case where a scale factor is zero, we give up resampling
    xFltIncr      = fltSmpPerCrossing * xFactMin1
    yFltIncr      = fltSmpPerCrossing * yFactMin1
    xGain         = fltGain * xFactMin1
    yGain         = fltGain * yFactMin1
  }

  /** Pulls image size parameters from `bufWidthIn` and `bufHeightIn`.
    * Returns the resulting image or matrix size.
    */
  protected final def pullWindowParams(off: Int): Int = {
    var newImageIn = false

    val _bufWidthIn = bufWidthIn
    if (_bufWidthIn != null && off < _bufWidthIn.size) {
      val value = max(1, _bufWidthIn.buf(off))
      if (widthIn != value) {
        widthIn     = value
        newImageIn  = true
      }
    }

    val _bufHeightIn = bufHeightIn
    if (_bufHeightIn != null && off < _bufHeightIn.size) {
      val value = max(1, _bufHeightIn.buf(off))
      if (heightIn != value) {
        heightIn    = value
        newImageIn  = true
      }
    }

    if (newImageIn) {
      winBuf = new Array[Double](widthIn * heightIn)
    }
    winBuf.length
  }

  /** Pulls interpolation parameters from `bufWrap`, `bufRollOff`, `bufKaiserBeta` and `butZeroCrossings`. */
  protected final def pullInterpParams(off: Int): Unit = {
    var newTable = false

//    println(s"pullInterpParams($off)")

    val _bufWrap = bufWrap
    if (_bufWrap != null && off < _bufWrap.size) {
      wrapBounds = _bufWrap.buf(off) != 0
    }

    val _bufRollOff = bufRollOff
    if (_bufRollOff != null && off < _bufRollOff.size) {
      val newRollOff = max(0.0, min(1.0, _bufRollOff.buf(off)))
      // println(s"newRollOff = $newRollOff")
      if (rollOff != newRollOff) {
        rollOff   = newRollOff
        newTable  = true
      }
    }

    val _bufKaiserBeta = bufKaiserBeta
    if (_bufKaiserBeta != null && off < _bufKaiserBeta.size) {
      val newKaiserBeta = max(0.0, _bufKaiserBeta.buf(off))
      // println(s"newKaiserBeta = $newKaiserBeta")
      if (kaiserBeta != newKaiserBeta) {
        kaiserBeta  = newKaiserBeta
        newTable    = true
      }
    }

    val _bufZeroCrossings = bufZeroCrossings
    if (_bufZeroCrossings != null && off < _bufZeroCrossings.size) {
      // a value of zero indicates bicubic interpolation,
      // a value greater than zero indicates band-limited sinc interpolation
      val newZeroCrossings = max(0, _bufZeroCrossings.buf(off))
      // println(s"newZeroCrossings = $newZeroCrossings")
      if (zeroCrossings != newZeroCrossings) {
        zeroCrossings = newZeroCrossings
        newTable      = true
      }
    }

    if (newTable) {
      // calculates low-pass filter kernel
      fltLenH = ((fltSmpPerCrossing * zeroCrossings) / rollOff + 0.5).toInt
      // println(s"newTable; fltLenH = $fltLenH")
      fltBuf  = new Array[Double](fltLenH)
      fltBufD = new Array[Double](fltLenH)
      if (zeroCrossings > 0) {
        fltGain = Filter.createAntiAliasFilter(
          fltBuf, fltBufD, halfWinSize = fltLenH, samplesPerCrossing = fltSmpPerCrossing, rollOff = rollOff,
          kaiserBeta = kaiserBeta)
        setScale(sx, sy)
      }
    }
  }

  protected final def calcValue(x: Double, y: Double): Double = {
    val _widthIn    = widthIn
    val _heightIn   = heightIn
    val _wrap       = wrapBounds
    val _winBuf     = winBuf

    val xq        = abs(x) % 1.0
    val xTi       = x.toInt
    val yq        = abs(y) % 1.0
    val yTi       = y.toInt

    // ------------------------ bicubic ------------------------
    if (zeroCrossings == 0) {

      val w1 = _widthIn  - 1
      val h1 = _heightIn - 1
      val x1 = if (_wrap) IntFunctions.wrap(xTi, 0, w1) else IntFunctions.clip(xTi, 0, w1)
      val y1 = if (_wrap) IntFunctions.wrap(yTi, 0, h1) else IntFunctions.clip(yTi, 0, h1)

      val value = if (xq < 1.0e-20 && yq < 1.0e-20) {
        // short cut
        val winBufOff = y1 * _widthIn + x1
        _winBuf(winBufOff)
      } else {
        // cf. https://en.wikipedia.org/wiki/Bicubic_interpolation
        // note -- we begin indices at `0` instead of `-1` here
        val x0  = if (x1 >  0) x1 - 1 else if (_wrap) w1 else 0
        val y0  = if (y1 >  0) y1 - 1 else if (_wrap) h1 else 0
        val x2  = if (x1 < w1) x1 + 1 else if (_wrap)  0 else w1
        val y2  = if (y1 < h1) y1 + 1 else if (_wrap)  0 else h1
        val x3  = if (x2 < w1) x2 + 1 else if (_wrap)  0 else w1
        val y3  = if (y2 < h1) y2 + 1 else if (_wrap)  0 else h1

        // XXX TODO --- we could save these multiplications here
        val y0s = y0 * _widthIn
        val y1s = y1 * _widthIn
        val y2s = y2 * _widthIn
        val y3s = y3 * _widthIn
        val f00 = _winBuf(y0s + x0)
        val f10 = _winBuf(y0s + x1)
        val f20 = _winBuf(y0s + x2)
        val f30 = _winBuf(y0s + x3)
        val f01 = _winBuf(y1s + x0)
        val f11 = _winBuf(y1s + x1)
        val f21 = _winBuf(y1s + x2)
        val f31 = _winBuf(y1s + x3)
        val f02 = _winBuf(y2s + x0)
        val f12 = _winBuf(y2s + x1)
        val f22 = _winBuf(y2s + x2)
        val f32 = _winBuf(y2s + x3)
        val f03 = _winBuf(y3s + x0)
        val f13 = _winBuf(y3s + x1)
        val f23 = _winBuf(y3s + x2)
        val f33 = _winBuf(y3s + x3)

        def bicubic(t: Double, f0: Double, f1: Double, f2: Double, f3: Double): Double = {
          // XXX TODO --- could save the next two multiplications
          val tt  = t * t
          val ttt = tt * t
          val c0  = 2 * f1
          val c1  = (-f0 + f2) * t
          val c2  = (2 * f0 - 5 * f1 + 4 * f2 - f3) * tt
          val c3  = (-f0  + 3 * f1 - 3 * f2 + f3) * ttt
          0.5 * (c0 + c1 + c2 + c3)
        }

        val b0 = bicubic(xq, f00, f10, f20, f30)
        val b1 = bicubic(xq, f01, f11, f21, f31)
        val b2 = bicubic(xq, f02, f12, f22, f32)
        val b3 = bicubic(xq, f03, f13, f23, f33)
        bicubic(yq, b0, b1, b2, b3)
      }
      value
    }
    // ------------------------- sinc -------------------------
    else {
      val _fltBuf   = fltBuf
      val _fltBufD  = fltBufD
      val _fltLenH  = fltLenH

      var value     = 0.0

      def xIter(dir: Boolean): Unit = {
        var xSrcOffI  = if (dir) xTi else xTi + 1
        val xq1       = if (dir) xq  else 1.0 - xq
        var xFltOff   = xq1 * xFltIncr
        var xFltOffI  = xFltOff.toInt
        var xSrcRem   = if (_wrap) Int.MaxValue else if (dir) xSrcOffI else _widthIn - xSrcOffI
        xSrcOffI      = IntFunctions.wrap(xSrcOffI, 0, _widthIn - 1)

        while ((xFltOffI < _fltLenH) && (xSrcRem > 0)) {
          val xr  = xFltOff % 1.0  // 0...1 for interpol.
          val xw  = _fltBuf(xFltOffI) + _fltBufD(xFltOffI) * xr

          def yIter(dir: Boolean): Unit = {
            var ySrcOffI  = if (dir) yTi else yTi + 1
            val yq1       = if (dir) yq  else 1.0 - yq
            var yFltOff   = yq1 * yFltIncr
            var yFltOffI  = yFltOff.toInt
            var ySrcRem   = if (_wrap) Int.MaxValue else if (dir) ySrcOffI else _heightIn - ySrcOffI
            ySrcOffI      = IntFunctions.wrap(ySrcOffI, 0, _heightIn - 1)

            while ((yFltOffI < _fltLenH) && (ySrcRem > 0)) {
              val yr        = yFltOff % 1.0  // 0...1 for interpol.
              val yw        = _fltBuf(yFltOffI) + _fltBufD(yFltOffI) * yr
              val winBufOff = ySrcOffI * _widthIn + xSrcOffI

              // if (winBufOff > _winBuf.length) {
              //   println(s"x $x, y $y, xT $xT, yT $yT, xSrcOffI $xSrcOffI, ySrcOffI $ySrcOffI, _widthIn ${_widthIn}, _heightIn ${_heightIn}")
              // }

              value += _winBuf(winBufOff) * xw * yw
              if (dir) {
                ySrcOffI -= 1
                if (ySrcOffI < 0) ySrcOffI += _heightIn
              } else {
                ySrcOffI += 1
                if (ySrcOffI == _heightIn) ySrcOffI = 0
              }
              ySrcRem  -= 1
              yFltOff  += yFltIncr
              yFltOffI  = yFltOff.toInt
            }
          }

          yIter(dir = true )  // left -hand side of window
          yIter(dir = false)  // right-hand side of window

          if (dir) {
            xSrcOffI -= 1
            if (xSrcOffI < 0) xSrcOffI += _widthIn
          } else {
            xSrcOffI += 1
            if (xSrcOffI == _widthIn) xSrcOffI = 0
          }
          xSrcRem  -= 1
          xFltOff  += xFltIncr
          xFltOffI  = xFltOff.toInt
        }
      }

      xIter(dir = true )  // left -hand side of window
      xIter(dir = false)  // right-hand side of window

      value * xGain * yGain
    }
  }
}