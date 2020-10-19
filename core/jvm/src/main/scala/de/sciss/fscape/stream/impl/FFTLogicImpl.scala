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

import akka.stream.{Attributes, FanInShape3, FanInShape4}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.impl.Handlers._
import de.sciss.fscape.stream.impl.logic.WindowedInDOutD
import de.sciss.fscape.stream.{BufD, BufI, Builder, Control, InD, InI, Layer, OutD, OutI}
import de.sciss.numbers
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D

import scala.annotation.switch
import scala.math.max

/** Base class for 1-dimensional FFT transforms. */
trait FFTLogicImpl extends WindowedInDOutD {
  _: Handlers[_] =>

  // ---- abstract ----

  protected def performFFT(): Unit

  // ---- impl ----

  protected final var fft: DoubleFFT_1D = _

  protected final var timeSize  : Int     = _
  protected final var fftSize   : Int     = -1
  protected final var gain      : Double  = _

  protected def gainFor(fftSize: Int): Double

  override protected def stopped(): Unit = {
    super.stopped()
    fft = null
  }

  protected final def setFFTSize(n: Int): Unit =
    if (fftSize != n) {
      fftSize = n
      fft     = new DoubleFFT_1D (n)
      gain    = gainFor(n)
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

  // ---- impl ----

  final val shape: Shape = new FanInShape4(
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

  // ---- impl ----

  final val shape: Shape = new FanInShape3(
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
  def createLogic(attr: Attributes): NodeImpl[Shape] = new Real1FFTLogicImpl(name, shape, layer)
}

final class Real1IFFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFTHalfStageImpl("Real1IFFT") {
  def createLogic(attr: Attributes): NodeImpl[Shape] = new Real1IFFTLogicImpl(name, shape, layer)
}

final class Real1FullFFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFTFullStageImpl("Real1FullFFT") {
  def createLogic(attr: Attributes): NodeImpl[Shape] = new Real1FullFFTLogicImpl(name, shape, layer)
}

final class Real1FullIFFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFTFullStageImpl("Real1FullIFFT") {
  def createLogic(attr: Attributes): NodeImpl[Shape] = new Real1FullIFFTLogicImpl(name, shape, layer)
}

final class Complex1FFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFTFullStageImpl("ComplexIFFT") {
  def createLogic(attr: Attributes): NodeImpl[Shape] = new Complex1FFTLogicImpl(name, shape, layer)
}

final class Complex1IFFTStageImpl(layer: Layer)(implicit ctrl: Control) extends FFTFullStageImpl("Complex1IFFT") {
  def createLogic(attr: Attributes): NodeImpl[Shape] = new Complex1IFFTLogicImpl(name, shape, layer)
}

abstract class FFTHalfLogicImpl(name: String, shape: FanInShape4[BufD, BufI, BufI, BufI, BufD], layer: Layer)
                               (implicit ctrl: Control)
  extends Handlers(name, layer, shape)
    with FFTLogicImpl {

  import numbers.Implicits._

  protected final val hIn     : InDMain   = InDMain  (this, shape.in0)
  protected final val hOut    : OutDMain  = OutDMain (this, shape.out)
  protected final val hSize   : InIAux    = InIAux   (this, shape.in1)(max(0, _))
  protected final val hPadding: InIAux    = InIAux   (this, shape.in2)(max(0, _))
  protected final val hMode   : InIAux    = InIAux   (this, shape.in3)(_.clip(0, 2))

  protected final var mode      : Int = _   // 0 - packed, 1 - unpacked, 2 - discarded

  // for half-spectra we add the extra "redundant" complex entry possibly needed for untangling DC and Nyquist
  final def winBufSize: Int = fftSize + 2
}

final class Real1FFTLogicImpl(name: String, shape: FanInShape4[BufD, BufI, BufI, BufI, BufD], layer: Layer)
                             (implicit ctrl: Control)
  extends FFTHalfLogicImpl(name, shape, layer) {

  override protected def readWinSize  : Long = timeSize
  override protected def writeWinSize : Long = if (mode == 1) fftSize + 2 else fftSize

  protected def gainFor(fftSize: Int): Double = 2.0 / fftSize

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
    Util.mul(fftBuf, 0, _fftSize, gain) // scale correctly
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

final class Real1IFFTLogicImpl(name: String, shape: FanInShape4[BufD, BufI, BufI, BufI, BufD], layer: Layer)
                                 (implicit ctrl: Control)
  extends FFTHalfLogicImpl(name, shape, layer) {

  override protected def readWinSize  : Long = if (mode == 1) fftSize + 2 else fftSize
  override protected def writeWinSize : Long = timeSize

  protected def gainFor(fftSize: Int): Double = {
    import numbers.Implicits._
    if (fftSize.isPowerOfTwo) 1.0 else 0.5  // XXX TODO: bug in JTransforms it seems
  }

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
    if (gain != 1.0) Util.mul(fftBuf, 0, timeSize, gain)
  }
}


abstract class FFTFullLogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD], layer: Layer)
                               (implicit ctrl: Control)
  extends Handlers(name, shape = shape, layer = layer)
    with FFTLogicImpl {

  protected final val hIn     : InDMain   = InDMain  (this, shape.in0)
  protected final val hOut    : OutDMain  = OutDMain (this, shape.out)
  protected final val hSize   : InIAux    = InIAux   (this, shape.in1)(max(0, _))
  protected final val hPadding: InIAux    = InIAux   (this, shape.in2)(max(0, _))

  final def winBufSize: Int = fftSize << 1
}

trait FFTFullForwardLogicImpl {
  _: FFTFullLogicImpl =>

  final protected def tryObtainWinParams(): Boolean = {
    val ok = hSize.hasNext && hPadding.hasNext
    if (ok) {
      timeSize      = hSize   .next()
      val padding   = hPadding.next()
      val _fftSize  = timeSize + padding
      setFFTSize(_fftSize)
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

  protected def gainFor(fftSize: Int): Double = 2.0 / fftSize

  protected def performFFT(): Unit = {
    val _fftBuf = winBuf
    fft.realForwardFull(_fftBuf)
    Util.mul(_fftBuf, 0, _fftBuf.length, gain) // scale correctly
  }
}

final class Real1FullIFFTLogicImpl(name: String, shape: FanInShape3[BufD, BufI, BufI, BufD], layer: Layer)
                                     (implicit ctrl: Control)
  extends FFTFullLogicImpl(name, shape, layer) with FFTFullBackwardLogicImpl {

  override protected def readWinSize  : Long = fftSize << 1
  override protected def writeWinSize : Long = timeSize

  protected def gainFor(fftSize: Int): Double = 0.5

  protected def performFFT(): Unit = {
    val _fftBuf = winBuf
    fft.complexInverse(_fftBuf, false)
    var i = 0
    var j = 0
    val g = gain
    while (j < _fftBuf.length) {
      _fftBuf(i) = _fftBuf(j) * g
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

  protected def gainFor(fftSize: Int): Double = 1.0 / fftSize

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

  protected def gainFor(fftSize: Int): Double = 1.0

  protected def performFFT(): Unit = {
    val _fftBuf = winBuf
    fft.complexInverse(_fftBuf, false)
  }
}