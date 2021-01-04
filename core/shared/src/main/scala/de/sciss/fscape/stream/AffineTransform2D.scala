/*
 *  AffineTransform2D.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape15, Inlet, Outlet}
import de.sciss.fscape.stream.impl.Handlers.{InAux, InDAux, InDMain, InIAux, OutDMain}
import de.sciss.fscape.stream.impl.logic.WindowedMultiInOut
import de.sciss.fscape.stream.impl.{Handlers, StageImpl}
import de.sciss.numbers.Implicits._
import de.sciss.numbers.IntFunctions

import scala.math.{abs, max, min, sqrt}

// XXX TODO --- should use ScanImageImpl
object AffineTransform2D {
  def apply(in: OutD, widthIn: OutI, heightIn: OutI, widthOut: OutI, heightOut: OutI,
            m00: OutD, m10: OutD, m01: OutD, m11: OutD, m02: OutD, m12: OutD, wrap: OutI,
            rollOff: OutD, kaiserBeta: OutD, zeroCrossings: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in            , stage.in0)
    b.connect(widthIn       , stage.in1)
    b.connect(heightIn      , stage.in2)
    b.connect(widthOut      , stage.in3)
    b.connect(heightOut     , stage.in4)
    b.connect(m00           , stage.in5)
    b.connect(m10           , stage.in6)
    b.connect(m01           , stage.in7)
    b.connect(m11           , stage.in8)
    b.connect(m02           , stage.in9)
    b.connect(m12           , stage.in10)
    b.connect(wrap          , stage.in11)
    b.connect(rollOff       , stage.in12)
    b.connect(kaiserBeta    , stage.in13)
    b.connect(zeroCrossings , stage.in14)
    stage.out
  }

  private final val name = "AffineTransform2D"

  private type Shape = FanInShape15[
    BufD, BufI, BufI, BufI, BufI,
    BufD, BufD, BufD, BufD, BufD, BufD,
    BufI, BufD, BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape15(
      in0  = InD (s"$name.in"),
      in1  = InI (s"$name.widthIn"),
      in2  = InI (s"$name.heightIn"),
      in3  = InI (s"$name.widthOut"),
      in4  = InI (s"$name.heightOut"),
      in5  = InD (s"$name.m00"),
      in6  = InD (s"$name.m10"),
      in7  = InD (s"$name.m01"),
      in8  = InD (s"$name.m11"),
      in9  = InD (s"$name.m02"),
      in10 = InD (s"$name.m12"),
      in11 = InI (s"$name.wrap"),
      in12 = InD (s"$name.rollOff"),
      in13 = InD (s"$name.kaiserBeta"),
      in14 = InI (s"$name.zeroCrossings"),
      out  = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) with WindowedMultiInOut {

    private[this] val hIn       : InDMain   = InDMain  (this, shape.in0)
    private[this] val hOut      : OutDMain  = OutDMain (this, shape.out)
    private[this] val hWidthIn  : InIAux    = InIAux   (this, shape.in1)(max(1, _))
    private[this] val hHeightIn : InIAux    = InIAux   (this, shape.in2)(max(1, _))
    private[this] val hWidthOut : InIAux    = InIAux   (this, shape.in3)(max(0, _))
    private[this] val hHeightOut: InIAux    = InIAux   (this, shape.in4)(max(0, _))
    private[this] val hM00      : InDAux    = InDAux   (this, shape.in5)()
    private[this] val hM10      : InDAux    = InDAux   (this, shape.in6)()
    private[this] val hM01      : InDAux    = InDAux   (this, shape.in7)()
    private[this] val hM11      : InDAux    = InDAux   (this, shape.in8)()
    private[this] val hM02      : InDAux    = InDAux   (this, shape.in9)()
    private[this] val hM12      : InDAux    = InDAux   (this, shape.in10)()
    private[this] val hWrap     : InIAux    = InIAux   (this, shape.in11)()
    private[this] val hRollOff  : InDAux    = InDAux   (this, shape.in12)(_.clip(0.0, 1.0))
    private[this] val hKaiser   : InDAux    = InDAux   (this, shape.in13)(max(0.0, _))
    private[this] val hZero     : InIAux    = InIAux   (this, shape.in14)(max(0, _))

    private[this] var winBuf    : Array[Double] = _
    private[this] var widthIn   : Int = _
    private[this] var heightIn  : Int = _
    private[this] var widthOut  : Int = _
    private[this] var heightOut : Int = _

    // -------- process ---------

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf = null
    }

    protected def mainInAvailable: Int =
      hIn.available

    protected def outAvailable: Int = {
      var res = hOut.available
      if (res == 0) return 0

      def add(i: InAux[_, _]): Unit = {
        val sz = i.available
        if (sz < res) res = sz
      }

      add(hM00); add(hM10); add(hM01); add(hM11); add(hM02); add(hM12)
      add(hWrap); add(hRollOff); add(hKaiser); add(hZero)
      res
    }

    protected def isHotIn(inlet: Inlet[_]): Boolean = true

    protected def mainInDone: Boolean = hIn .isDone
    protected def outDone   : Boolean = hOut.isDone
    protected def flushOut(): Boolean = hOut.flush()

    override protected def onDone(outlet: Outlet[_]): Unit =
      super.onDone(outlet)

    protected def tryObtainWinParams(): Boolean = {
      val ok =
        hWidthIn  .hasNext &&
        hHeightIn .hasNext &&
        hWidthOut .hasNext &&
        hHeightOut.hasNext
//        hM00      .hasNext &&
//        hM10      .hasNext &&
//        hM01      .hasNext &&
//        hM11      .hasNext &&
//        hM02      .hasNext &&
//        hM12      .hasNext &&
//        hWrap     .hasNext &&
//        hRollOff  .hasNext &&
//        hKaiser   .hasNext &&
//        hZero     .hasNext

      if (ok) {
        var newImageIn = false
        val _widthIn = hWidthIn.next()
        if (widthIn != _widthIn) {
          widthIn     = _widthIn
          newImageIn  = true
        }
        val _heightIn = hHeightIn.next()
        if (heightIn != _heightIn) {
          heightIn    = _heightIn
          newImageIn  = true
        }
        val _widthOut = hWidthOut.next()
        widthOut  = if (_widthOut   == 0) _widthIn  else _widthOut
        val _heightOut = hHeightOut.next()
        heightOut = if (_heightOut  == 0) _heightIn else _heightOut
        if (newImageIn) {
          winBuf = new Array[Double](_widthIn * _heightIn)
        }

//        // the following are aux inputs that are polled during window
//        // processing. by calling `peek` we ensure that ... NO
//        hM00.peek
      }
      ok
    }

    protected def readWinSize : Long = widthIn  * heightIn
    protected def writeWinSize: Long = widthOut * heightOut

    protected def processWindow(): Unit = {
      val offI  = readOff.toInt
      val chunk = winBuf.length - offI
      if (chunk > 0) {
        Util.clear(winBuf, offI, chunk)
      }
    }

    protected def readIntoWindow(chunk: Int): Unit =
      hIn.nextN(winBuf, readOff.toInt, chunk)

    private[this] var mi00   = 0.0
    private[this] var mi10   = 0.0
    private[this] var mi01   = 0.0
    private[this] var mi11   = 0.0
    private[this] var mi02   = 0.0
    private[this] var mi12   = 0.0

    private[this] var m00   = 0.0
    private[this] var m10   = 0.0
    private[this] var m01   = 0.0
    private[this] var m11   = 0.0
    private[this] var m02   = 0.0
    private[this] var m12   = 0.0

    private[this] var rollOff       = -1.0  // must be negative for init detection
    private[this] var kaiserBeta    = -1.0
    private[this] var zeroCrossings = -1

    private[this] var fltLenH     : Int           = _
    private[this] var fltBuf      : Array[Double] = _
    private[this] var fltBufD     : Array[Double] = _
    private[this] var fltGain     : Double        = _

    private[this] val fltSmpPerCrossing = 4096

    // calculates low-pass filter kernel
    @inline
    private[this] def updateTable(): Unit = {
      fltLenH = ((fltSmpPerCrossing * zeroCrossings) / rollOff + 0.5).toInt
      fltBuf  = new Array[Double](fltLenH)
      fltBufD = new Array[Double](fltLenH)
      fltGain = Filter.createAntiAliasFilter(
        fltBuf, fltBufD, halfWinSize = fltLenH, samplesPerCrossing = fltSmpPerCrossing, rollOff = rollOff,
        kaiserBeta = kaiserBeta)
    }

    // calculates inverted matrix
    @inline
    private[this] def updateMatrix(): Unit = {
      val det = mi00 * mi11 - mi01 * mi10
      m00 =  mi11 / det
      m10 = -mi10 / det
      m01 = -mi01 / det
      m11 =  mi00 / det
      m02 = (mi01 * mi12 - mi11 * mi02) / det
      m12 = (mi10 * mi02 - mi00 * mi12) / det
    }

    private[this] var xFactor = 0.0
    private[this] var yFactor = 0.0

    protected def writeFromWindow(chunk: Int): Unit = {
      val out         = hOut.array
      var outOff      = hOut.offset
      val outStop     = outOff + chunk
      val _widthIn    = widthIn
      val _heightIn   = heightIn
      val _widthOut   = widthOut
      val _winBuf     = winBuf
      var newTable    = false
      var newMatrix   = false

      val imgOutOff   = writeOff.toInt
      var x           = imgOutOff % _widthOut
      var y           = imgOutOff / _widthOut

      // updated by `matrixChanged`
      var _m00, _m10, _m01, _m11, _m02, _m12  = 0.0
      var xFltIncr, yFltIncr, xGain, yGain    = 0.0

      def matrixChanged(): Unit = {
        _m00 = m00
        _m10 = m10
        _m01 = m01
        _m11 = m11
        _m02 = m02
        _m12 = m12
        xFactor       = sqrt(_m00 * _m00 + _m10 * _m10)
        yFactor       = sqrt(_m01 * _m01 + _m11 * _m11)
        val xFactMin1 = if (xFactor == 0) 1.0 else min(1.0, xFactor)
        val yFactMin1 = if (yFactor == 0) 1.0 else min(1.0, yFactor)
        // for the malformed case where a scale factor is zero, we give up resampling
        xFltIncr      = fltSmpPerCrossing * xFactMin1
        yFltIncr      = fltSmpPerCrossing * yFactMin1
        xGain         = fltGain * xFactMin1
        yGain         = fltGain * yFactMin1
      }

      matrixChanged()

      while (outOff < outStop) {
        val _mi00 = hM00.next()
        if (mi00 != _mi00) {
          mi00      = _mi00
          newMatrix = true
        }

        val _mi10 = hM10.next()
        if (mi10 != _mi10) {
          mi10      = _mi10
          newMatrix = true
        }

        val _mi01 = hM01.next()
        if (mi01 != _mi01) {
          mi01      = _mi01
          newMatrix = true
        }

        val _mi11 = hM11.next()
        if (mi11 != _mi11) {
          mi11      = _mi11
          newMatrix = true
        }

        val _mi02 = hM02.next()
        if (mi02 != _mi02) {
          mi02      = _mi02
          newMatrix = true
        }

        val _mi12 = hM12.next()
        if (mi12 != _mi12) {
          mi12      = _mi12
          newMatrix = true
        }

        val _wrap = hWrap.next() > 0

        val _rollOff = hRollOff.next()
        if (rollOff != _rollOff) {
          rollOff   = _rollOff
          newTable  = true
        }

        val _kaiserBeta = hKaiser.next()
        if (kaiserBeta != _kaiserBeta) {
          kaiserBeta  = _kaiserBeta
          newTable    = true
        }

        // a value of zero indicates bicubic interpolation,
        // a value greater than zero indicates band-limited sinc interpolation
        val _zeroCrossings = hZero.next()
        if (zeroCrossings != _zeroCrossings) {
          zeroCrossings = _zeroCrossings
          newTable      = true
        }

        if (newMatrix) {
          updateMatrix()
          matrixChanged()
          newMatrix = false
        }
        
        // [ x']   [  m00  m01  m02  ] [ x ]   [ m00x + m01y + m02 ]
        // [ y'] = [  m10  m11  m12  ] [ y ] = [ m10x + m11y + m12 ]
        // [ 1 ]   [   0    0    1   ] [ 1 ]   [         1         ]

        val xT = _m00 * x + _m01 * y + _m02
        val yT = _m10 * x + _m11 * y + _m12

        if (newTable) {
          if (zeroCrossings > 0) {
            updateTable()
            val xFactMin1 = if (xFactor == 0) 1.0 else min(1.0, xFactor)
            val yFactMin1 = if (yFactor == 0) 1.0 else min(1.0, yFactor)
            xGain         = fltGain * xFactMin1
            yGain         = fltGain * yFactMin1
          }
          newTable = false
        }

        val xq        = abs(xT) % 1.0
        val xTi       = xT.toInt
        val yq        = abs(yT) % 1.0
        val yTi       = yT.toInt

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
          out(outOff) = value
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

                  //                if (winBufOff > _winBuf.length) {
                  //                  println(s"x $x, y $y, xT $xT, yT $yT, xSrcOffI $xSrcOffI, ySrcOffI $ySrcOffI, _widthIn ${_widthIn}, _heightIn ${_heightIn}")
                  //                }

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

          out(outOff) = value * xGain * yGain
        }

        outOff    += 1
        x          += 1
        if (x == _widthOut) {
          x  = 0
          y += 1
        }
      }
      
      hOut.advance(chunk)
    }
  }
}