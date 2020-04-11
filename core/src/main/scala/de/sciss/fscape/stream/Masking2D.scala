/*
 *  Masking2D.scala
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

package de.sciss.fscape.stream

import akka.stream.{Attributes, FanInShape8}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InDMain, InIAux, OutDMain}
import de.sciss.fscape.stream.impl.logic.WindowedInA1A2OutB
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.numbers

import scala.math.max

object Masking2D {
  def apply(fg: OutD, bg: OutD, rows: OutI, columns: OutI, threshNoise: OutD, threshMask: OutD,
            blurRows: OutI, blurColumns: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(fg          , stage.in0)
    b.connect(bg          , stage.in1)
    b.connect(rows        , stage.in2)
    b.connect(columns     , stage.in3)
    b.connect(threshNoise , stage.in4)
    b.connect(threshMask  , stage.in5)
    b.connect(blurRows    , stage.in6)
    b.connect(blurColumns , stage.in7)
    stage.out
  }

  private final val name = "Masking2D"

  private type Shp = FanInShape8[BufD, BufD, BufI, BufI, BufD, BufD, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape8(
      in0 = InD (s"$name.fg"          ),
      in1 = InD (s"$name.bg"          ),
      in2 = InI (s"$name.rows"        ),
      in3 = InI (s"$name.columns"     ),
      in4 = InD (s"$name.threshNoise" ),
      in5 = InD (s"$name.threshMask"  ),
      in6 = InI (s"$name.blurRows"    ),
      in7 = InI (s"$name.blurColumns" ),
      out = OutD(s"$name.out"         )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape)
      with WindowedInA1A2OutB[Double, BufD, Double, BufD, Double, BufD, Double] {

    protected     val hIn1        : InDMain  = InDMain  (this, shape.in0)
    protected     val hIn2        : InDMain  = InDMain  (this, shape.in1)
    protected     val hOut        : OutDMain = OutDMain (this, shape.out)
    private[this] val hRows       : InIAux   = InIAux   (this, shape.in2)(max(1, _))
    private[this] val hCols       : InIAux   = InIAux   (this, shape.in3)(max(1, _))
    private[this] val hThreshNoise: InDAux   = InDAux   (this, shape.in4)(max(0.0, _))
    private[this] val hThreshMask : InDAux   = InDAux   (this, shape.in5)(max(0.0, _))
    private[this] val hBlurRows   : InIAux   = InIAux   (this, shape.in6)(max(1, _))
    private[this] val hBlurCols   : InIAux   = InIAux   (this, shape.in7)(max(1, _))

    private[this] var rows        = -1
    private[this] var columns     = -1
    private[this] var winSize     = -1

    private[this] var blurRows    = -1
    private[this] var blurColumns = -1

    private[this] var threshNoise = 0.0
    private[this] var threshMask  = 0.0

    private[this] var fgVec   : Array[Double] = _
    private[this] var bgVec   : Array[Double] = _
    private[this] var fgMat   : Array[Double] = _
    private[this] var bgMat   : Array[Double] = _
    private[this] var outVec  : Array[Double] = _
    private[this] var kernel  : Array[Double] = _

    protected def a1Tpe : StreamType[Double, BufD] = StreamType.double
    protected def a2Tpe : StreamType[Double, BufD] = StreamType.double
    protected def bTpe  : StreamType[Double, BufD] = StreamType.double
    protected def wTpe  : StreamType[Double, BufD] = StreamType.double

    protected def clearWindowTail(): Unit = ()

    protected def newWindowBuffer(n: Int): Array[Double] = new Array(n)

    override protected def stopped(): Unit = {
      super.stopped()
      fgVec   = null
      bgVec   = null
      outVec  = null
      kernel  = null
      fgMat   = null
      bgMat   = null
    }

    protected def tryObtainWinParams(): Boolean = {
      val ok =
        hRows       .hasNext &&
        hCols       .hasNext &&
        hThreshNoise.hasNext &&
        hThreshMask .hasNext &&
        hBlurRows   .hasNext &&
        hBlurCols   .hasNext

      if (ok) {
        var newKernel = false
        var newWin    = false

        val _rows = hRows.next()
        if (rows != _rows) {
          rows = _rows
          newWin = true
        }
        val _columns = hCols.next()
        if (columns != _columns) {
          columns = _columns
          newWin = true
        }
        threshNoise = hThreshNoise.next()
        threshMask  = hThreshMask.next()
        val _blurRows = hBlurRows.next()
        if (blurRows != _blurRows) {
          blurRows = _blurRows
          newKernel = true
        }
        val _blurColumns = hBlurCols.next()
        if (blurColumns != _blurColumns) {
          blurColumns = _blurColumns
          newKernel = true
        }

        if (newWin) {
          winSize = rows * columns
          fgVec   = new Array(rows)
          bgVec   = new Array(rows)
          outVec  = new Array(rows)
          fgMat   = new Array(winSize)
          bgMat   = new Array(winSize)
        }

        if (newKernel) {
          val _blurRows     = blurRows
          val _blurColumns  = blurColumns
          val kCols         = _blurColumns * 2 + 1
          val kRows         = _blurRows * 2 + 1
          val kernelSize    = kCols * kRows
          kernel = Array.tabulate(kernelSize) { i =>
            import numbers.Implicits._
            val col = i % kCols
            val row = i / kCols
            val ci  = col absDif _blurColumns
            val ri  = row absDif _blurRows
            // blurs refer to -60 dB point
            val dc      = ci.toDouble / _blurColumns
            val dr      = ri.toDouble / _blurRows
            val dampCol = 0.001.pow(dc)
            val dampRow = 0.001.pow(dr)
            math.sqrt(dampCol * dampRow)
            //          math .min(dampCol , dampRow)
            //          math.sqrt((dampCol.squared + dampRow.squared) / 2)

            //          val dd = math.sqrt(dc.squared + dr.squared)
            //          0.001.pow(dd)
          }
        }
      }
      ok
    }

    protected def winBufSize: Int = 0

    override protected def readWinSize  : Long = rows
    override protected def writeWinSize : Long = rows

    protected def readIntoWindow(chunk: Int): Unit = {
      val off = readOff.toInt
      hIn1.nextN(fgMat, off, chunk)
      hIn2.nextN(bgMat, off, chunk)
    }

    protected def writeFromWindow(chunk: Int): Unit = {
      val inOff = writeOff.toInt // readFromWinOff.toInt
      hOut.nextN(outVec, inOff, chunk)
    }

    protected def processWindow(): Unit = {
      val off   = readOff.toInt // writeToWinOff.toInt
      val _fgV  = fgVec
      val _bgV  = bgVec
      val _rows = rows
      val _cols = columns
      if (off < _rows) {
        Util.clear(_fgV, off, _rows - off)
        Util.clear(_bgV, off, _rows - off)
      }

      /*

        - we fill the output vector with ones
        - we iterate over the inner column (which will correspond to the output).
        - for each cell, apply the kernel to the fg matrix (with max'ing).
        - we obtained a smooth sample of the foreground
        - is that sample above the noise-floor? if not, skip to next iteration
        - if yes, we do the same for the background, then calculate the
          "unit attenuation"
        - we multiply the corresponding cell of the output vector with the
          unit attenuation, and proceed to the neighbours, using the kernel damping
          inverse: mul = att * damp[i] + 1.0 * (1.0 - damp[i]) = (att - 1.0) * damp[i] + 1.0

       */

      val _fgMat  = fgMat
      val _bgMat  = bgMat

      // move the matrices to the left
      var ri = 0
      var i = 0
      while (ri < _rows) {
        System.arraycopy(_fgMat, i + 1, _fgMat, i, _cols - 1)
        System.arraycopy(_bgMat, i + 1, _bgMat, i, _cols - 1)
        i += _cols
        _fgMat(i - 1) = _fgV(ri)
        _bgMat(i - 1) = _bgV(ri)
        ri += 1
      }

      val _vec  = outVec
      Util.fill(_vec, 0, _rows, 1.0)

      val cc            = _cols/2
      val _blurCols     = math.min(cc, blurColumns)
      val _blurRows     = blurRows
      val kCols         = _blurCols * 2 + 1
      val _kernel       = kernel
      val _threshNoise  = threshNoise
      val _threshMask   = threshMask
      ri = 0
      while (ri < _rows) {
        var vf      = _fgMat(ri * _cols + cc)
        var vb      = _bgMat(ri * _cols + cc)
        val dyStart = Math.max(-ri           , -_blurRows)
        val dyStop  = Math.min(_rows - ri - 1, +_blurRows)
        var dy      = dyStart
        var ky      = (dy + _blurRows) * kCols + _blurCols
        var my      = (ri + dy) * _cols + cc
        while (dy <= dyStop) {
          var dx  = -_blurCols
          while (dx <= _blurCols) {
            val att   = _kernel(ky + dx)
            val fTmp  = _fgMat (my + dx) * att
            val bTmp  = _bgMat (my + dx) * att
            if (fTmp > vf) vf = fTmp
            if (bTmp > vb) vb = bTmp
            dx += 1
          }
          dy += 1
          ky += kCols
          my += _cols
        }

        if (vf > _threshNoise) {
          val ratio = vb / vf
          if (ratio > _threshMask) {
            val att = _threshMask / ratio
            /*

              e.g. threshNoise is 0.5, vf = 1.2, vb = 1.0, then ratio = 0.833, then att = 0.6,
              then adjusted we find vb' = 0.6, and ratio' = 0.5 = threshNoise

             */

            val attM1 = att - 1.0
            dy  = dyStart
            ky  = (dy + _blurRows) * kCols + _blurCols
            while (dy <= dyStop) {
              val damp  = _kernel(ky)
              val mul   = attM1 * damp + 1.0
              _vec(ri + dy) *= mul
              dy += 1
              ky += kCols
            }
          }
        }

        ri += 1
      }
    }
  }
}
