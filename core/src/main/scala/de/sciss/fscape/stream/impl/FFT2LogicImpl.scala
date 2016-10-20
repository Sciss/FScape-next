/*
 *  FFT2LogicImpl.scala
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

package de.sciss.fscape.stream
package impl

import akka.stream.stage.GraphStageLogic
import akka.stream.{Attributes, FanInShape3, FanInShape4}
import de.sciss.fscape.Util
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_2D

import scala.annotation.switch

abstract class FFT2HalfStageImpl(name: String)
  extends StageImpl[FanInShape4[BufD, BufI, BufI, BufI, BufD]](name) {

  // ---- impl ----

  final val shape = new FanInShape4(
    in0 = InD (s"$name.in"     ),
    in1 = InI (s"$name.rows"   ),
    in2 = InI (s"$name.columns"),
    in3 = InI (s"$name.mode"   ),
    out = OutD(s"$name.out"    )
  )

  final def connect(in: OutD, rows: OutI, columns: OutI, mode: OutI)(implicit b: Builder): OutD = {
    val stage   = b.add(this)
    b.connect(in      , stage.in0)
    b.connect(rows    , stage.in1)
    b.connect(columns , stage.in2)
    b.connect(mode    , stage.in3)

    stage.out
  }
}

abstract class FFT2FullStageImpl(name: String)
  extends StageImpl[FanInShape3[BufD, BufI, BufI, BufD]](name) {

  // ---- impl ----

  final val shape = new FanInShape3(
    in0 = InD (s"$name.in"     ),
    in1 = InI (s"$name.rows"   ),
    in2 = InI (s"$name.columns"),
    out = OutD(s"$name.out"    )
  )

  final def connect(in: OutD, rows: OutI, columns: OutI)(implicit b: Builder): OutD = {
    val stage   = b.add(this)
    b.connect(in      , stage.in0)
    b.connect(rows    , stage.in1)
    b.connect(columns , stage.in2)

    stage.out
  }
}

/** Base class for 1-dimensional FFT transforms. */
trait FFT2LogicImpl extends Node {
  _: GraphStageLogic =>

  // ---- abstract ----

  protected def performFFT(fft: DoubleFFT_2D, fftBuf: Array[Double]): Unit

  protected def inSize (nominal: Int): Int
  protected def outSize(nominal: Int): Int

  protected def bufIn0 : BufD
  protected def bufOut0: BufD

  // ---- impl ----

  protected final var fft       : DoubleFFT_2D  = _
  protected final var fftBuf    : Array[Double] = _
  protected final var fftRows = 0  // refreshed as `rows`
  protected final var fftCols = 0  // refreshed as `columns`
  protected final var fftSize = 0  // refreshed as `rows * columns`

  override protected def stopped(): Unit = {
    super.stopped()
    fft = null
  }

  protected final def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit =
    Util.copy(bufIn0.buf, inOff, fftBuf, writeToWinOff, chunk)

  protected final def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit =
    Util.copy(fftBuf, readFromWinOff, bufOut0.buf, outOff, chunk)

  protected final def processWindow(writeToWinOff: Int): Int = {
    Util.fill(fftBuf, writeToWinOff, fftBuf.length - writeToWinOff, 0.0)
    performFFT(fft, fftBuf)
    outSize(fftSize)
  }
}

abstract class FFT2HalfLogicImpl(name: String, shape: FanInShape4[BufD, BufI, BufI, BufI, BufD])
                               (implicit ctrl: Control)
  extends NodeImpl(name, shape)
    with FFT2LogicImpl
    with WindowedLogicImpl[FanInShape4[BufD, BufI, BufI, BufI, BufD]]
    with FilterLogicImpl[BufD, FanInShape4[BufD, BufI, BufI, BufI, BufD]]
    with FilterIn4DImpl[BufD, BufI, BufI, BufI] {

  private[this] final var rows      : Int = _
  private[this] final var columns   : Int = _
  protected     final var mode      : Int = _   // 0 - packed, 1 - unpacked, 2 - discarded

  protected final def startNextWindow(inOff: Int): Int = {
    if (bufIn1 != null && inOff < bufIn1.size) {
      rows = math.max(1, bufIn1.buf(inOff))
    }
    if (bufIn2 != null && inOff < bufIn2.size) {
      columns = math.max(1, bufIn2.buf(inOff))
    }
    if (bufIn3 != null && inOff < bufIn3.size) {
      mode = math.max(0, math.min(2, bufIn3.buf(inOff)))
    }
    val size = rows * columns
    if (rows != fftRows || columns != fftCols) {
      fftRows = rows
      fftCols = columns
      fftSize = size
      fft     = new DoubleFFT_2D(rows, columns)
      // for half-spectra we add the extra "redundant" complex entry possibly needed for untangling DC and Nyquist
      fftBuf  = new Array[Double](size) // NOT YET: (n + 2)
    }
    inSize(size)
  }
}

abstract class FFT2FullLogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD])
                                (implicit ctrl: Control)
  extends NodeImpl(name, shape)
    with FFT2LogicImpl
    with WindowedLogicImpl[FanInShape3[BufD, BufI, BufI, BufD]]
    with FilterLogicImpl[BufD, FanInShape3[BufD, BufI, BufI, BufD]]
    with FilterIn3DImpl[BufD, BufI, BufI] {

  private[this] final var rows      : Int = _
  private[this] final var columns   : Int = _

  protected final def startNextWindow(inOff: Int): Int = {
    if (bufIn1 != null && inOff < bufIn1.size) {
      rows = math.max(1, bufIn1.buf(inOff))
    }
    if (bufIn2 != null && inOff < bufIn2.size) {
      columns = math.max(1, bufIn2.buf(inOff))
    }
    val size = rows * columns
    if (rows != fftRows || columns != fftCols) {
      fftRows = rows
      fftCols = columns
      fftSize = size
      fft     = new DoubleFFT_2D(rows, columns)
      fftBuf  = new Array[Double](size << 1)
    }
    inSize(size)
  }
}

final class Real2FFTStageImpl()(implicit ctrl: Control) extends FFT2HalfStageImpl("Real2FFT") {
  def createLogic(attr: Attributes) = new Real2FFTLogicImpl(name, shape)
}

final class Real2FFTLogicImpl(name: String, shape: FanInShape4[BufD, BufI, BufI, BufI, BufD])
                             (implicit ctrl: Control)
  extends FFT2HalfLogicImpl(name, shape) {

  protected def inSize (nominal: Int): Int = nominal
  protected def outSize(nominal: Int): Int = if (mode == 1) nominal + 2 else nominal

  protected def performFFT(fft: DoubleFFT_2D, fftBuf: Array[Double]): Unit = {
    fft.realForward(fftBuf)
    val _fftSize = fftSize
    Util.mul(fftBuf, 0, _fftSize, 2.0 / _fftSize) // scale correctly
    (mode: @switch) match {
      case 0 => // packed
      case 1 => // unpacked
        ???
        // move Re(Nyquist) from Im(DC)
        fftBuf(_fftSize)      = fftBuf(1)
        fftBuf(1)             = 0.0
        fftBuf(_fftSize + 1)  = 0.0
      case 2 => // discarded
        ???
        fftBuf(1)             = 0.0
    }
  }
}

final class Real2IFFTStageImpl()(implicit ctrl: Control) extends FFT2HalfStageImpl("Real2IFFT") {
  def createLogic(attr: Attributes) = new Real2IFFTLogicImpl(name, shape)
}

final class Real2IFFTLogicImpl(name: String, shape: FanInShape4[BufD, BufI, BufI, BufI, BufD])(implicit ctrl: Control)
  extends FFT2HalfLogicImpl(name, shape) {

  protected def inSize (nominal: Int): Int = if (mode == 1) nominal + 2 else nominal
  protected def outSize(nominal: Int): Int = nominal

  protected def performFFT(fft: DoubleFFT_2D, fftBuf: Array[Double]): Unit = {
    (mode: @switch) match {
      case 0 => // packed
      case 1 => // unpacked
        // move Re(Nyquist) to Im(DC)
        ???
        fftBuf(1) = fftBuf(fftSize)
      case 2 => // discarded
        ???
        fftBuf(1) = 0.0
    }
    fft.realInverse(fftBuf, false)
  }
}

final class Real2FullFFTStageImpl()(implicit ctrl: Control) extends FFT2FullStageImpl("Real2FullFFT") {
  def createLogic(attr: Attributes) = new Real2FullFFT2LogicImpl(name, shape)
}

final class Real2FullFFT2LogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD])(implicit ctrl: Control)
  extends FFT2FullLogicImpl(name, shape) {

  protected def inSize (nominal: Int): Int = nominal
  protected def outSize(nominal: Int): Int = nominal << 1

  protected def performFFT(fft: DoubleFFT_2D, fftBuf: Array[Double]): Unit = {
    fft.realForwardFull(fftBuf)
    Util.mul(fftBuf, 0, fftBuf.length, 2.0 / fftSize) // scale correctly
  }
}

final class Real2FullIFFTStageImpl()(implicit ctrl: Control) extends FFT2FullStageImpl("Real1FullIFFT") {
  def createLogic(attr: Attributes) = new Real1FullIFFT2LogicImpl(name, shape)
}

final class Real1FullIFFT2LogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD])(implicit ctrl: Control)
  extends FFT2FullLogicImpl(name, shape) {

  protected def inSize (nominal: Int): Int = nominal << 1
  protected def outSize(nominal: Int): Int = nominal

  protected def performFFT(fft: DoubleFFT_2D, fftBuf: Array[Double]): Unit = {
    // fft.realInverseFull(fftBuf, false)
    fft.complexInverse(fftBuf, false)
    var i = 0
    var j = 0
    while (j < fftBuf.length) {
      fftBuf(i) = fftBuf(j) * 0.5
      i += 1
      j += 2
    }
  }
}

final class Complex2FFTStageImpl()(implicit ctrl: Control) extends FFT2FullStageImpl("Complex2FFT") {
  def createLogic(attr: Attributes) = new Complex2FFT2LogicImpl(name, shape)
}

final class Complex2FFT2LogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD])(implicit ctrl: Control)
  extends FFT2FullLogicImpl(name, shape) {

  protected def inSize (nominal: Int): Int = nominal << 1
  protected def outSize(nominal: Int): Int = nominal << 1

  protected def performFFT(fft: DoubleFFT_2D, fftBuf: Array[Double]): Unit = {
    fft.complexForward(fftBuf)
    Util.mul(fftBuf, 0, fftBuf.length, 1.0 / fftSize) // scale correctly
  }
}

final class Complex2IFFTStageImpl()(implicit ctrl: Control) extends FFT2FullStageImpl("Complex2IFFT") {
  def createLogic(attr: Attributes) = new Complex2IFFT2LogicImpl(name, shape)
}

final class Complex2IFFT2LogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD])(implicit ctrl: Control)
  extends FFT2FullLogicImpl(name, shape) {

  protected def inSize (nominal: Int): Int = nominal << 1
  protected def outSize(nominal: Int): Int = nominal << 1

  protected def performFFT(fft: DoubleFFT_2D, fftBuf: Array[Double]): Unit =
    fft.complexInverse(fftBuf, false)
}