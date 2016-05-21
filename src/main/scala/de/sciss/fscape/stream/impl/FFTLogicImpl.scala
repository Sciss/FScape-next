/*
 *  FFTLogicImpl.scala
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

package de.sciss.fscape.stream.impl

import akka.NotUsed
import akka.stream.scaladsl.GraphDSL
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.{BufD, BufI, Control}
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D

abstract class FFTStageImpl extends GraphStage[FanInShape3[BufD, BufI, BufI, BufD]] {
  // ---- abstract ----
  
  protected def name: String
  
  // ---- impl ----
  
  final val shape = new FanInShape3(
    in0 = Inlet [BufD](s"$name.in"     ),
    in1 = Inlet [BufI](s"$name.size"   ),
    in2 = Inlet [BufI](s"$name.padding"),
    out = Outlet[BufD](s"$name.out"    )
  )

  final def connect(in: Outlet[BufD], size: Outlet[BufI], padding: Outlet[BufI])
                   (implicit b: GraphDSL.Builder[NotUsed]): Outlet[BufD] = {
    val stage   = b.add(this)
    import GraphDSL.Implicits._
    in      ~> stage.in0
    size    ~> stage.in1
    padding ~> stage.in2

    stage.out
  }
}

/** Base class for 1-dimensional FFT transforms. */
abstract class FFTLogicImpl(protected val shape: FanInShape3[BufD, BufI, BufI, BufD],
                            protected val ctrl: Control)
  extends GraphStageLogic(shape)
    with WindowedFilterLogicImpl[BufD, BufD, FanInShape3[BufD, BufI, BufI, BufD]]
    with FilterIn3Impl                            [BufD, BufI, BufI, BufD] {

  // ---- abstract ----

  protected def performFFT(fft: DoubleFFT_1D, fftBuf: Array[Double]): Unit

  protected def fftInSizeFactor : Int
  protected def fftOutSizeFactor: Int

  // ---- impl ----

  private[this] final var fft       : DoubleFFT_1D  = _
  private[this] final var fftBuf    : Array[Double] = _

  private[this] final var size      : Int = _  // already multiplied by `fftInSizeFactor`
  private[this] final var padding   : Int = _  // already multiplied by `fftInSizeFactor`

  private[this] final var _fftSize        = 0  // refreshed as `size + padding`

  override def postStop(): Unit = {
    super.postStop()
    fft = null
  }

  protected final def in0: Inlet[BufD] = shape.in0

  protected final def allocOutBuf(): BufD = ctrl.borrowBufD()

  protected final def fftSize: Int = _fftSize

  protected final def startNextWindow(inOff: Int): Int = {
    val inF = fftInSizeFactor
    if (bufIn1 != null && inOff < bufIn1.size) {
      size = math.max(1, bufIn1.buf(inOff)) * inF
    }
    if (bufIn2 != null && inOff < bufIn2.size) {
      padding = math.max(0, bufIn2.buf(inOff)) * inF
    }
    val n = (size + padding) / inF
    if (n != _fftSize) {
      _fftSize = n
      fft     = new DoubleFFT_1D (n)
      fftBuf  = new Array[Double](n * math.max(inF, fftOutSizeFactor))
    }
    size
  }

  protected final def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit =
    Util.copy(bufIn0.buf, inOff, fftBuf, writeToWinOff, chunk)

  protected final def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit =
    Util.copy(fftBuf, readFromWinOff, bufOut.buf, outOff, chunk)

  protected final def processWindow(writeToWinOff: Int): Int = {
    Util.fill(fftBuf, writeToWinOff, fftBuf.length - writeToWinOff, 0.0)
    performFFT(fft, fftBuf)
    _fftSize * fftOutSizeFactor
  }
}

final class Real1FFTStageImpl(ctrl: Control) extends FFTStageImpl {
  val name = "Real1FFT"
  
  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Real1FFTLogicImpl(shape, ctrl)
}

final class Real1FFTLogicImpl(shape: FanInShape3[BufD, BufI, BufI, BufD], ctrl: Control)
  extends FFTLogicImpl(shape, ctrl) {

  protected val fftInSizeFactor  = 1
  protected val fftOutSizeFactor = 1

  protected def performFFT(fft: DoubleFFT_1D, fftBuf: Array[Double]): Unit = {
    fft.realForward(fftBuf)
    Util.mul(fftBuf, 0, fftBuf.length, 2.0 / fftSize) // scale correctly
  }
}

final class Real1IFFTStageImpl(ctrl: Control) extends FFTStageImpl {
  val name = "Real1IFFT"

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Real1IFFTLogicImpl(shape, ctrl)
}

final class Real1IFFTLogicImpl(shape: FanInShape3[BufD, BufI, BufI, BufD], ctrl: Control)
  extends FFTLogicImpl(shape, ctrl) {

  protected val fftInSizeFactor  = 1
  protected val fftOutSizeFactor = 1

  protected def performFFT(fft: DoubleFFT_1D, fftBuf: Array[Double]): Unit =
    fft.realInverse(fftBuf, false)
}

final class Real1FullFFTStageImpl(ctrl: Control) extends FFTStageImpl {
  val name = "Real1FullFFT"

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Real1FullFFTLogicImpl(shape, ctrl)
}

final class Real1FullFFTLogicImpl(shape: FanInShape3[BufD, BufI, BufI, BufD], ctrl: Control)
  extends FFTLogicImpl(shape, ctrl) {

  protected val fftInSizeFactor  = 1
  protected val fftOutSizeFactor = 2

//  private var DEBUG = true

  protected def performFFT(fft: DoubleFFT_1D, fftBuf: Array[Double]): Unit = {
//    if (DEBUG) {
//      import de.sciss.file._
//      import de.sciss.synth.io._
//      val afBefore = AudioFile.openWrite(userHome/"Music"/"work"/"_NEXT_FFT_IN.aif",
//        AudioFileSpec(numChannels = 1, sampleRate = 44100.0))
//      val afBuf = Array(fftBuf.map(_.toFloat))
//      afBefore.write(afBuf, 0, fftBuf.length)
//      afBefore.close()
//    }

    fft.realForwardFull(fftBuf)
    // fft.realForward(fftBuf)
    Util.mul(fftBuf, 0, fftBuf.length, 2.0 / fftSize) // scale correctly

//    if (DEBUG) {
//      import de.sciss.file._
//      import de.sciss.synth.io._
//      val afAfter = AudioFile.openWrite(userHome/"Music"/"work"/"_NEXT_FFT.aif",
//        AudioFileSpec(numChannels = 1, sampleRate = 44100.0))
//      val afBuf = Array(fftBuf.map(_.toFloat))
//      afAfter.write(afBuf)
//      afAfter.close()
//
//      DEBUG = false
//    }
  }
}

final class Real1FullIFFTStageImpl(ctrl: Control) extends FFTStageImpl {
  val name = "Real1FullIFFT"

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Real1FullIFFTLogicImpl(shape, ctrl)
}

final class Real1FullIFFTLogicImpl(shape: FanInShape3[BufD, BufI, BufI, BufD], ctrl: Control)
  extends FFTLogicImpl(shape, ctrl) {

  protected val fftInSizeFactor  = 2
  protected val fftOutSizeFactor = 1

  protected def performFFT(fft: DoubleFFT_1D, fftBuf: Array[Double]): Unit = {
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

final class Complex1FFTStageImpl(ctrl: Control) extends FFTStageImpl {
  val name = "Complex1FFT"

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Complex1FFTLogicImpl(shape, ctrl)
}

final class Complex1FFTLogicImpl(shape: FanInShape3[BufD, BufI, BufI, BufD], ctrl: Control)
  extends FFTLogicImpl(shape, ctrl) {

  protected val fftInSizeFactor  = 2
  protected val fftOutSizeFactor = 2

  protected def performFFT(fft: DoubleFFT_1D, fftBuf: Array[Double]): Unit = {
    fft.complexForward(fftBuf)
    Util.mul(fftBuf, 0, fftBuf.length, 1.0 / fftSize) // scale correctly
  }
}

final class Complex1IFFTStageImpl(ctrl: Control) extends FFTStageImpl {
  val name = "Complex1IFFT"

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Complex1IFFTLogicImpl(shape, ctrl)
}

final class Complex1IFFTLogicImpl(shape: FanInShape3[BufD, BufI, BufI, BufD], ctrl: Control)
  extends FFTLogicImpl(shape, ctrl) {

  protected val fftInSizeFactor  = 2
  protected val fftOutSizeFactor = 2

  protected def performFFT(fft: DoubleFFT_1D, fftBuf: Array[Double]): Unit =
    fft.complexInverse(fftBuf, false)
}