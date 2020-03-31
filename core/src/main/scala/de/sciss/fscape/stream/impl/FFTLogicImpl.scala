/*
 *  FFTLogicImpl.scala
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

package de.sciss.fscape.stream.impl

import akka.stream.{Attributes, FanInShape3, FanInShape4, Shape}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.{BufD, BufI, Builder, Control, InD, InI, Layer, OutD, OutI, StreamType}
import de.sciss.numbers
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D

import scala.annotation.switch

// XXX TODO rename in next major version
/** Base class for 1-dimensional FFT transforms. */
trait FFTLogicImpl[S <: Shape] extends WindowedLogicNew[Double, BufD, S] {
  _: Handlers[S] =>

  // ---- abstract ----

  protected def performFFT(): Unit

  // ---- impl ----

  protected final val aTpe: StreamType[Double, BufD] = StreamType.double

  protected final var fft: DoubleFFT_1D = _

  protected final var timeSize  : Int = _
  protected final var fftSize   : Int = _

  protected final def setFFTSize(n: Int): Unit =
    if (fftSize != n) {
      fftSize = n
      fft     = new DoubleFFT_1D (n)
    }

  override protected final def processWindow(): Unit = {
    val fftBuf  = winBuf
    val offI    = readOff.toInt
    Util.clear(fftBuf, offI, fftBuf.length - offI)
    performFFT()
  }
}


abstract class FFTHalfStageImpl(name: String)
  extends StageImpl[FanInShape4[BufD, BufI, BufI, BufI, BufD]](name) {

  type S = FanInShape4[BufD, BufI, BufI, BufI, BufD]

  // ---- impl ----

  final val shape: S = new FanInShape4(
    in0 = InD (s"$name.in"     ),
    in1 = InI (s"$name.size"   ),
    in2 = InI (s"$name.padding"),
    in3 = InI (s"$name.mode"   ),
    out = OutD(s"$name.out"    )
  )

  final def connect(in: OutD, size: OutI, padding: OutI, mode: OutI)(implicit b: Builder): OutD = {
    val stage   = b.add(this)
    b.connect(in      , stage.in0)
    b.connect(size    , stage.in1)
    b.connect(padding , stage.in2)
    b.connect(mode    , stage.in3)

    stage.out
  }
}

abstract class FFTFullStageImpl(name: String)
  extends StageImpl[FanInShape3[BufD, BufI, BufI, BufD]](name) {

  type S = FanInShape3[BufD, BufI, BufI, BufD]

  // ---- impl ----

  final val shape = new FanInShape3(
    in0 = InD (s"$name.in"     ),
    in1 = InI (s"$name.size"   ),
    in2 = InI (s"$name.padding"),
    out = OutD(s"$name.out"    )
  )

  final def connect(in: OutD, size: OutI, padding: OutI)(implicit b: Builder): OutD = {
    val stage   = b.add(this)
    b.connect(in      , stage.in0)
    b.connect(size    , stage.in1)
    b.connect(padding , stage.in2)

    stage.out
  }
}

final class Real1FFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFTHalfStageImpl("Real1FFT") {
  def createLogic(attr: Attributes): NodeImpl[S] = new Real1FFTLogicImpl(name, shape, layer)
}

final class Real1IFFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFTHalfStageImpl("Real1IFFT") {
  def createLogic(attr: Attributes): NodeImpl[S] = new Real1IFFTLogicImpl(name, shape, layer)
}

final class Real1FullFFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFTFullStageImpl("Real1FullFFT") {
  def createLogic(attr: Attributes): NodeImpl[S] = new Real1FullFFTLogicImpl(name, shape, layer)
}

final class Real1FullIFFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFTFullStageImpl("Real1FullIFFT") {
  def createLogic(attr: Attributes): NodeImpl[S] = new Real1FullIFFTLogicImpl(name, shape, layer)
}

final class Complex1FFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFTFullStageImpl("ComplexIFFT") {
  def createLogic(attr: Attributes): NodeImpl[S] = new Complex1FFTLogicImpl(name, shape, layer)
}

final class Complex1IFFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFTFullStageImpl("Complex1IFFT") {
  def createLogic(attr: Attributes): NodeImpl[S] = new Complex1IFFTLogicImpl(name, shape, layer)
}

// XXX TODO rename in next major version
abstract class FFTHalfLogicImpl(name: String, shape: FanInShape4[BufD, BufI, BufI, BufI, BufD], layer: Layer)
                               (implicit ctrl: Control)
  extends Handlers[FanInShape4[BufD, BufI, BufI, BufI, BufD]](name, shape = shape, layer = layer)
    with FFTLogicImpl[FanInShape4[BufD, BufI, BufI, BufI, BufD]] {

  import numbers.Implicits._

  protected final val hIn       = new Handlers.InDMain  (this, shape.in0)()
  protected final val hOut      = new Handlers.OutDMain (this, shape.out)
  protected final val hSize     = new Handlers.InIAux   (this, shape.in1)(_.max(0))
  protected final val hPadding  = new Handlers.InIAux   (this, shape.in2)(_.max(0))
  protected final val hMode     = new Handlers.InIAux   (this, shape.in3)(_.clip(0, 2))

  protected final var mode      : Int = _   // 0 - packed, 1 - unpacked, 2 - discarded

  override protected def stopped(): Unit = {
    super.stopped()
    fft = null
    hIn     .free()
    hOut    .free()
    hSize   .free()
    hPadding.free()
    hMode   .free()
  }

  // for half-spectra we add the extra "redundant" complex entry possibly needed for untangling DC and Nyquist
  final def winBufSize: Int = fftSize + 2
}

// XXX TODO rename in next major version
final class Real1FFTLogicImpl(name: String, shape: FanInShape4[BufD, BufI, BufI, BufI, BufD], layer: Layer)
                             (implicit ctrl: Control)
  extends FFTHalfLogicImpl(name, shape, layer) {

  override protected def readWinSize  : Long = timeSize
  override protected def writeWinSize : Long = if (mode == 1) fftSize + 2 else fftSize

  protected def tryObtainWinParams(): Boolean = {
    val ok = hSize.hasNext && hPadding.hasNext && hMode.hasNext
    if (ok) {
      timeSize    = hSize   .next()
      val padding = hPadding.next()
      mode        = hMode   .next()
      setFFTSize(timeSize + padding)
    }
    ok
  }

  protected def performFFT(): Unit = {
    val fftBuf    = winBuf
    val _fftSize  = fftSize
    fft.realForward(fftBuf)
    Util.mul(fftBuf, 0, _fftSize, 2.0 / _fftSize) // scale correctly
    (mode: @switch) match {
      case 0 => // packed
      case 1 => // unpacked
        // move Re(Nyquist) from Im(DC)
        fftBuf(_fftSize)      = fftBuf(1)
        fftBuf(1)             = 0.0
        fftBuf(_fftSize + 1)  = 0.0
      case 2 => // discarded
        fftBuf(1)             = 0.0
    }
  }
}

// XXX TODO rename in next major version
final class Real1IFFTLogicImpl(name: String, shape: FanInShape4[BufD, BufI, BufI, BufI, BufD], layer: Layer)
                                 (implicit ctrl: Control)
  extends FFTHalfLogicImpl(name, shape, layer) {

  override protected def readWinSize  : Long = if (mode == 1) fftSize + 2 else fftSize
  override protected def writeWinSize : Long = timeSize

  protected def tryObtainWinParams(): Boolean = {
    val ok = hSize.hasNext && hPadding.hasNext && hMode.hasNext
    if (ok) {
      val _fftSize  = hSize   .next()
      val padding   = hPadding.next()
      mode          = hMode   .next()
      timeSize      = _fftSize - padding
      setFFTSize(_fftSize)
    }
    ok
  }

  protected def performFFT(): Unit = {
    val fftBuf = winBuf
    (mode: @switch) match {
      case 0 => // packed
      case 1 => // unpacked
        // move Re(Nyquist) to Im(DC)
        fftBuf(1) = fftBuf(fftSize)
      case 2 => // discarded
        fftBuf(1) = 0.0
    }
    fft.realInverse(fftBuf, false)
  }
}


// XXX TODO rename in next major version
abstract class FFTFullLogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD], layer: Layer)
                                  (implicit ctrl: Control)
  extends Handlers[FanInShape3[BufD, BufI, BufI, BufD]](name, shape = shape, layer = layer)
    with FFTLogicImpl[FanInShape3[BufD, BufI, BufI, BufD]] {

  protected final val hIn       = new Handlers.InDMain  (this, shape.in0)()
  protected final val hOut      = new Handlers.OutDMain (this, shape.out)
  protected final val hSize     = new Handlers.InIAux   (this, shape.in1)(_.max(0))
  protected final val hPadding  = new Handlers.InIAux   (this, shape.in2)(_.max(0))

  override protected def stopped(): Unit = {
    super.stopped()
    fft = null
    hIn     .free()
    hOut    .free()
    hSize   .free()
    hPadding.free()
  }

  final def winBufSize: Int = fftSize << 1
}

trait FFTFullForwardLogicImpl {
  _: FFTFullLogicImpl =>

  final protected def tryObtainWinParams(): Boolean = {
    val ok = hSize.hasNext && hPadding.hasNext
    if (ok) {
      timeSize    = hSize   .next()
      val padding = hPadding.next()
      setFFTSize(timeSize + padding)
    }
    ok
  }
}

trait FFTFullBackwardLogicImpl {
  _: FFTFullLogicImpl =>

  protected def tryObtainWinParams(): Boolean = {
    val ok = hSize.hasNext && hPadding.hasNext
    if (ok) {
      val _fftSize  = hSize   .next()
      val padding   = hPadding.next()
      timeSize      = _fftSize - padding
      setFFTSize(_fftSize)
    }
    ok
  }
}

final class Real1FullFFTLogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD], layer: Layer)
                                 (implicit ctrl: Control)
  extends FFTFullLogicImpl(name, shape, layer) with FFTFullForwardLogicImpl {

  override protected def readWinSize  : Long = timeSize
  override protected def writeWinSize : Long = fftSize << 1

  protected def performFFT(): Unit = {
    val _fftBuf = winBuf
    fft.realForwardFull(_fftBuf)
    Util.mul(_fftBuf, 0, _fftBuf.length, 2.0 / fftSize) // scale correctly
  }
}

final class Real1FullIFFTLogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD], layer: Layer)
                                     (implicit ctrl: Control)
  extends FFTFullLogicImpl(name, shape, layer) with FFTFullBackwardLogicImpl {

  override protected def readWinSize  : Long = fftSize << 1
  override protected def writeWinSize : Long = timeSize

  protected def performFFT(): Unit = {
    val _fftBuf = winBuf
    fft.complexInverse(_fftBuf, false)
    var i = 0
    var j = 0
    while (j < _fftBuf.length) {
      _fftBuf(i) = _fftBuf(j) * 0.5
      i += 1
      j += 2
    }
  }
}

final class Complex1FFTLogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD], layer: Layer)
                                (implicit ctrl: Control)
  extends FFTFullLogicImpl(name, shape, layer) with FFTFullForwardLogicImpl {

  override protected def readWinSize  : Long = timeSize << 1
  override protected def writeWinSize : Long = fftSize  << 1

  protected def performFFT(): Unit = {
    val _fftBuf = winBuf
    fft.complexForward(_fftBuf)
    Util.mul(_fftBuf, 0, _fftBuf.length, 1.0 / fftSize) // scale correctly
  }
}

final class Complex1IFFTLogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD], layer: Layer)
                                 (implicit ctrl: Control)
  extends FFTFullLogicImpl(name, shape, layer) with FFTFullBackwardLogicImpl {

  override protected def readWinSize  : Long = fftSize  << 1
  override protected def writeWinSize : Long = timeSize << 1

  protected def performFFT(): Unit = {
    val _fftBuf = winBuf
    fft.complexInverse(_fftBuf, false)
  }
}