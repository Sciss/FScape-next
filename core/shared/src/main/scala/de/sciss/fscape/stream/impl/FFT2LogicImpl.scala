/*
 *  FFT2LogicImpl.scala
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

package de.sciss.fscape.stream
package impl

import akka.stream.{Attributes, FanInShape3, FanInShape4}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.impl.Handlers.{InDMain, InIAux, OutDMain}
import de.sciss.fscape.stream.impl.logic.WindowedInDOutD
import de.sciss.numbers
import de.sciss.transform4s.fft.DoubleFFT_2D

import scala.annotation.switch
import scala.math.max

/** Base class for 2-dimensional FFT transforms. */
trait FFT2LogicImpl extends WindowedInDOutD {
  this: Handlers[_] =>

  // ---- abstract ----

  protected def performFFT(): Unit

  // ---- impl ----

  protected final var fft       : DoubleFFT_2D  = _

  protected final var fftRows: Int = -1  // refreshed as `rows`
  protected final var fftCols: Int = -1  // refreshed as `columns`
  protected final var fftSize: Int = -1  // refreshed as `rows * columns`

  protected final var gain      : Double  = _

  protected def gainFor(fftSize: Int): Double

  override protected def stopped(): Unit = {
    super.stopped()
    fft = null
  }

  protected final def setFFTSize(r: Int, c: Int): Unit =
    if (fftRows != r || fftCols != c) {
      fftSize = r * c
      fftRows = r
      fftCols = c
      fft     = DoubleFFT_2D(rows = r, columns = c)
      gain    = gainFor(fftSize)
    }

  override protected final def processWindow(): Unit = {
    val fftBuf  = winBuf
    val offI    = readOff.toInt
    Util.clear(fftBuf, offI, fftBuf.length - offI)
    performFFT()
  }
}

abstract class FFT2HalfStageImpl(name: String)
  extends StageImpl[FanInShape4[BufD, BufI, BufI, BufI, BufD]](name) {

  // ---- impl ----

  final val shape: Shape = new FanInShape4(
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

  final val shape: Shape = new FanInShape3(
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

final class Real2FFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFT2HalfStageImpl("Real2FFT") {
  def createLogic(attr: Attributes): NodeImpl[Shape] = new Real2FFTLogicImpl(name, shape, layer)
}

final class Real2IFFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFT2HalfStageImpl("Real2IFFT") {
  def createLogic(attr: Attributes): NodeImpl[Shape] = new Real2IFFTLogicImpl(name, shape, layer)
}

final class Real2FullFFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFT2FullStageImpl("Real2FullFFT") {
  def createLogic(attr: Attributes): NodeImpl[Shape] = new Real2FullFFT2LogicImpl(name, shape, layer)
}

final class Real2FullIFFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFT2FullStageImpl("Real1FullIFFT") {
  def createLogic(attr: Attributes): NodeImpl[Shape] = new Real1FullIFFT2LogicImpl(name, shape, layer)
}

final class Complex2FFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFT2FullStageImpl("Complex2FFT") {
  def createLogic(attr: Attributes): NodeImpl[Shape] = new Complex2FFT2LogicImpl(name, shape, layer)
}

final class Complex2IFFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFT2FullStageImpl("Complex2IFFT") {
  def createLogic(attr: Attributes): NodeImpl[Shape] = new Complex2IFFT2LogicImpl(name, shape, layer)
}

abstract class FFT2HalfLogicImpl(name: String, shape: FanInShape4[BufD, BufI, BufI, BufI, BufD], layer: Layer)
                               (implicit ctrl: Control)
  extends Handlers(name, layer, shape)
    with FFT2LogicImpl {

  import numbers.Implicits._

  protected final val hIn   : InDMain   = InDMain  (this, shape.in0)
  protected final val hOut  : OutDMain  = OutDMain (this, shape.out)
  protected final val hRows : InIAux    = InIAux   (this, shape.in1)(max(0, _))
  protected final val hCols : InIAux    = InIAux   (this, shape.in2)(max(0, _))
  protected final val hMode : InIAux    = InIAux   (this, shape.in3)(_.clip(0, 2))

  protected final var mode  : Int = _   // 0 - packed, 1 - unpacked, 2 - discarded

  final def winBufSize: Int = fftSize // + 2 --- XXX TODO --- we only implemented packed more now

  //  protected final def startNextWindow(inOff: Int): Long = {
//    if (bufIn1 != null && inOff < bufIn1.size) {
//      rows = math.max(1, bufIn1.buf(inOff))
//    }
//    if (bufIn2 != null && inOff < bufIn2.size) {
//      columns = math.max(1, bufIn2.buf(inOff))
//    }
//    if (bufIn3 != null && inOff < bufIn3.size) {
//      mode = math.max(0, math.min(2, bufIn3.buf(inOff)))
//    }
//    val size = rows * columns
//    if (rows != fftRows || columns != fftCols) {
//      fftRows = rows
//      fftCols = columns
//      fftSize = size
//      fft     = new DoubleFFT_2D(rows, columns)
//      // for half-spectra we add the extra "redundant" complex entry possibly needed for untangling DC and Nyquist
//      fftBuf  = new Array[Double](size) // NOT YET: (n + 2)
//    }
//    inSize(size)
//  }
}

trait FFT2RealLogicImpl {
  this: FFT2HalfLogicImpl=>

  final protected def tryObtainWinParams(): Boolean = {
    val ok = hRows.hasNext && hCols.hasNext && hMode.hasNext
    if (ok) {
      val r = hRows.next()
      val c = hCols.next()
      mode  = hMode.next()
      setFFTSize(r = r, c = c)
    }
    ok
  }
}
final class Real2FFTLogicImpl(name: String, shape: FanInShape4[BufD, BufI, BufI, BufI, BufD], layer: Layer)
                             (implicit ctrl: Control)
  extends FFT2HalfLogicImpl(name, shape, layer) with FFT2RealLogicImpl {

  override protected def readWinSize  : Long = fftSize
  override protected def writeWinSize : Long = /*if (mode == 1) fftSize + 2 else*/ fftSize  // XXX TODO

  protected def gainFor(fftSize: Int): Double = 2.0 / fftSize

  protected def performFFT(): Unit = {
    val fftBuf    = winBuf
    val _fftSize  = fftSize
    fft.realForward(fftBuf)
    Util.mul(fftBuf, 0, _fftSize, gain)
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

final class Real2IFFTLogicImpl(name: String, shape: FanInShape4[BufD, BufI, BufI, BufI, BufD], layer: Layer)
                              (implicit ctrl: Control)
  extends FFT2HalfLogicImpl(name, shape, layer) with FFT2RealLogicImpl {

  override protected def readWinSize  : Long = /*if (mode == 1) fftSize + 2 else*/ fftSize  // XXX TODO
  override protected def writeWinSize : Long = fftSize

  protected def gainFor(fftSize: Int): Double = 1.0

  protected def performFFT(): Unit = {
    val fftBuf = winBuf
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
    fft.realInverse(fftBuf, scale = false)
    // if (gain != 1.0) Util.mul(fftBuf, 0, fftSize, gain)
  }
}

abstract class FFT2FullLogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD], layer: Layer)
                                (implicit ctrl: Control)
  extends Handlers(name, layer, shape)
    with FFT2LogicImpl {

  protected final val hIn     : InDMain   = InDMain  (this, shape.in0)
  protected final val hOut    : OutDMain  = OutDMain (this, shape.out)
  protected final val hRows   : InIAux    = InIAux   (this, shape.in1)(max(0, _))
  protected final val hCols   : InIAux    = InIAux   (this, shape.in2)(max(0, _))

  final def winBufSize: Int = fftSize << 1

  final protected def tryObtainWinParams(): Boolean = {
    val ok = hRows.hasNext && hCols.hasNext
    if (ok) {
      val r = hRows.next()
      val c = hCols.next()
      setFFTSize(r = r, c = c)
    }
    ok
  }
}

final class Real2FullFFT2LogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD], layer: Layer)
                                  (implicit ctrl: Control)
  extends FFT2FullLogicImpl(name, shape, layer) {

  override protected def readWinSize  : Long = fftSize
  override protected def writeWinSize : Long = fftSize << 1

  protected def gainFor(fftSize: Int): Double = 2.0 / fftSize

  protected def performFFT(): Unit = {
    val fftBuf = winBuf
    fft.realForwardFull(fftBuf)
    Util.mul(fftBuf, 0, fftBuf.length, gain)
  }
}

final class Real1FullIFFT2LogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD], layer: Layer)
                                   (implicit ctrl: Control)
  extends FFT2FullLogicImpl(name, shape, layer) {

  override protected def readWinSize  : Long = fftSize << 1
  override protected def writeWinSize : Long = fftSize

  protected def gainFor(fftSize: Int): Double = 0.5

  protected def performFFT(): Unit = {
    val fftBuf = winBuf
    // fft.realInverseFull(fftBuf, false)
    fft.complexInverse(fftBuf, scale = false)
    var i = 0
    var j = 0
    val g = gain
    while (j < fftBuf.length) {
      fftBuf(i) = fftBuf(j) * g
      i += 1
      j += 2
    }
  }
}

final class Complex2FFT2LogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD], layer: Layer)
                                 (implicit ctrl: Control)
  extends FFT2FullLogicImpl(name, shape, layer) {

  override protected def readWinSize  : Long = fftSize << 1
  override protected def writeWinSize : Long = fftSize << 1

  protected def gainFor(fftSize: Int): Double = 1.0 / fftSize

  protected def performFFT(): Unit = {
    val fftBuf = winBuf
    fft.complexForward(fftBuf)
    Util.mul(fftBuf, 0, fftBuf.length, gain)
  }
}

final class Complex2IFFT2LogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD], layer: Layer)
                                  (implicit ctrl: Control)
  extends FFT2FullLogicImpl(name, shape, layer) {

  override protected def readWinSize  : Long = fftSize << 1
  override protected def writeWinSize : Long = fftSize << 1

  protected def gainFor(fftSize: Int): Double = 1.0

  protected def performFFT(): Unit = {
    val fftBuf = winBuf
    fft.complexInverse(fftBuf, scale = false)
    // if (gain != 1.0) Util.mul(fftBuf, 0, fftSize, gain)
  }
}