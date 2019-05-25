/*
 *  Masking2D.scala
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

package de.sciss.fscape.stream

import akka.stream.{Attributes, FanInShape8, Outlet}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.impl.{DemandAuxInHandler, DemandInOutImpl, DemandProcessInHandler, DemandWindowedLogic, NodeImpl, Out1DoubleImpl, Out1LogicImpl, ProcessOutHandlerImpl, StageImpl}
import de.sciss.numbers

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

  private type Shape = FanInShape8[BufD, BufD, BufI, BufI, BufD, BufD, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape8(
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

    def createLogic(attr: Attributes): NodeImpl[Masking2D.Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with DemandWindowedLogic[Shape]
      with Out1DoubleImpl     [Shape]
      with Out1LogicImpl[BufD, Shape]
      with DemandInOutImpl    [Shape] {

    private[this] var bufIn0 : BufD = _
    private[this] var bufIn1 : BufD = _
    private[this] var bufIn2 : BufI = _
    private[this] var bufIn3 : BufI = _
    private[this] var bufIn4 : BufD = _
    private[this] var bufIn5 : BufD = _
    private[this] var bufIn6 : BufI = _
    private[this] var bufIn7 : BufI = _

    protected var bufOut0: BufD = _

    private[this] var _mainCanRead  = false
    private[this] var _auxCanRead   = false
    private[this] var _mainInValid  = false
    private[this] var _auxInValid   = false
    private[this] var _inValid      = false

    protected def out0: Outlet[BufD] = shape.out

    def mainCanRead : Boolean = _mainCanRead
    def auxCanRead  : Boolean = _auxCanRead
    def mainInValid : Boolean = _mainInValid
    def auxInValid  : Boolean = _auxInValid
    def inValid     : Boolean = _inValid

    new DemandProcessInHandler(shape.in0, this)
    new DemandProcessInHandler(shape.in1, this)
    new DemandAuxInHandler    (shape.in2, this)
    new DemandAuxInHandler    (shape.in3, this)
    new DemandAuxInHandler    (shape.in4, this)
    new DemandAuxInHandler    (shape.in5, this)
    new DemandAuxInHandler    (shape.in6, this)
    new DemandAuxInHandler    (shape.in7, this)
    new ProcessOutHandlerImpl (shape.out, this)

//    override def preStart(): Unit = {
//      val sh = shape
//      pull(sh.in0)
//      pull(sh.in1)
//      pull(sh.in2)
//      pull(sh.in3)
//      pull(sh.in4)
//      pull(sh.in5)
//      pull(sh.in6)
//      pull(sh.in7)
//    }

    override protected def stopped(): Unit = {
      super.stopped()
      fgVec   = null
      bgVec   = null
      outVec  = null
      kernel  = null
      fgMat   = null
      bgMat   = null
      freeInputBuffers()
      freeOutputBuffers()
    }

    protected def readMainIns(): Int = {
      freeMainInBuffers()
      val sh        = shape
      bufIn0        = grab(sh.in0)
      bufIn0.assertAllocated()
      tryPull(sh.in0)

      bufIn1        = grab(sh.in1)
      bufIn1.assertAllocated()
      tryPull(sh.in1)

      if (!_mainInValid) {
        _mainInValid= true
        _inValid    = _auxInValid
      }

      _mainCanRead = false
      math.min(bufIn0.size, bufIn1.size)
    }

    private def freeInputBuffers(): Unit = {
      freeMainInBuffers()
      freeAuxInBuffers()
    }

    private def freeAuxInBuffers(): Unit = {
      if (bufIn2 != null) {
        bufIn2.release()
        bufIn2 = null
      }
      if (bufIn3 != null) {
        bufIn3.release()
        bufIn3 = null
      }
      if (bufIn4 != null) {
        bufIn4.release()
        bufIn4 = null
      }
      if (bufIn5 != null) {
        bufIn5.release()
        bufIn5 = null
      }
      if (bufIn6 != null) {
        bufIn6.release()
        bufIn6 = null
      }
      if (bufIn7 != null) {
        bufIn7.release()
        bufIn7 = null
      }
    }

    private def freeMainInBuffers(): Unit = {
      if (bufIn0 != null) {
        bufIn0.release()
        bufIn0 = null
      }
      if (bufIn1 != null) {
        bufIn1.release()
        bufIn1 = null
      }
    }

    protected def readAuxIns(): Int = {
      freeAuxInBuffers()
      val sh    = shape
      var sz    = 0

      if (isAvailable(sh.in2)) {
        bufIn2  = grab(sh.in2)
        sz      = math.max(sz, bufIn2.size)
        tryPull(sh.in2)
      }
      if (isAvailable(sh.in3)) {
        bufIn3  = grab(sh.in3)
        sz      = math.max(sz, bufIn3.size)
        tryPull(sh.in3)
      }
      if (isAvailable(sh.in4)) {
        bufIn4  = grab(sh.in4)
        sz      = math.max(sz, bufIn4.size)
        tryPull(sh.in4)
      }
      if (isAvailable(sh.in5)) {
        bufIn5  = grab(sh.in5)
        sz      = math.max(sz, bufIn5.size)
        tryPull(sh.in5)
      }
      if (isAvailable(sh.in6)) {
        bufIn6  = grab(sh.in6)
        sz      = math.max(sz, bufIn6.size)
        tryPull(sh.in6)
      }
      if (isAvailable(sh.in7)) {
        bufIn7  = grab(sh.in7)
        sz      = math.max(sz, bufIn7.size)
        tryPull(sh.in7)
      }

      if (!_auxInValid) {
        _auxInValid = true
        _inValid    = _mainInValid
      }

      _auxCanRead = false
      sz
    }

    def updateAuxCanRead(): Unit = {
      val sh = shape
      _auxCanRead =
        ((isClosed(sh.in2) && _auxInValid) || isAvailable(sh.in2)) &&
          ((isClosed(sh.in3) && _auxInValid) || isAvailable(sh.in3)) &&
          ((isClosed(sh.in4) && _auxInValid) || isAvailable(sh.in4)) &&
          ((isClosed(sh.in5) && _auxInValid) || isAvailable(sh.in5)) &&
          ((isClosed(sh.in6) && _auxInValid) || isAvailable(sh.in6)) &&
          ((isClosed(sh.in7) && _auxInValid) || isAvailable(sh.in7))
    }

    def updateMainCanRead(): Unit = {
      val sh = shape
      _mainCanRead = isAvailable(sh.in0) && isAvailable(sh.in1)
    }

    protected def inputsEnded: Boolean = {
      val sh = shape
      mainInRemain == 0 &&
        ((isClosed(sh.in0) && !isAvailable(sh.in0)) || (isClosed(sh.in1) && !isAvailable(sh.in1)))
    }

    protected def freeOutputBuffers(): Unit =
      if (bufOut0 != null) {
        bufOut0.release()
        bufOut0 = null
      }

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

    protected def startNextWindow(): Long = {
      var newKernel = false
      var newWin    = false

      // 2: rows, 3: columns, 4: threshNoise, 5: threshMask, 6: blurRows, 7: blurColumns
      val inOff = auxInOff
      if (bufIn2 != null && inOff < bufIn2.size) {
        val _rows = math.max(1, bufIn2.buf(inOff))
        if (rows != _rows) {
          rows = _rows
          newWin = true
        }
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        val _columns = math.max(1, bufIn3.buf(inOff))
        if (columns != _columns) {
          columns = _columns
          newWin = true
        }
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        threshNoise = math.max(0.0, bufIn4.buf(inOff))
      }
      if (bufIn5 != null && inOff < bufIn5.size) {
        threshMask = math.max(0.0, bufIn5.buf(inOff))
      }
      if (bufIn6 != null && inOff < bufIn6.size) {
        val _blurRows = math.max(1, bufIn6.buf(inOff))
        if (blurRows != _blurRows) {
          blurRows = _blurRows
          newKernel = true
        }
      }
      if (bufIn7 != null && inOff < bufIn7.size) {
        val _blurColumns = math.max(1, bufIn7.buf(inOff))
        if (blurColumns != _blurColumns) {
          blurColumns = _blurColumns
          newKernel = true
        }
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

      rows
    }

    protected def canStartNextWindow: Boolean = auxInRemain > 0 || (auxInValid && {
      val sh = shape
      import sh._
      isClosed(in2) && isClosed(in3) && isClosed(in4) && isClosed(in5) && isClosed(in6) && isClosed(in7)
    })

    protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit = {
      val off = writeToWinOff.toInt
      Util.copy(bufIn0.buf, mainInOff, fgVec, off, chunk)
      Util.copy(bufIn1.buf, mainInOff, bgVec, off, chunk)
    }

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      val inOff = readFromWinOff.toInt
      Util.copy(outVec, inOff, bufOut0.buf, outOff, chunk)
    }

    protected def processWindow(writeToWinOff: Long): Long = {
      val off   = writeToWinOff.toInt
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

      _rows
    }
  }
}
