package de.sciss.fscape.stream.impl

import akka.NotUsed
import akka.stream.scaladsl.GraphDSL
import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.{BufD, BufI, Control}
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D

import scala.annotation.tailrec

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
  extends GraphStageLogic(shape) with FilterIn3Impl[BufD, BufI, BufI, BufD] {

  // ---- abstract ----

  protected def performFFT(fft: DoubleFFT_1D, fftBuf: Array[Double]): Unit

  protected def fftInSizeFactor : Int
  protected def fftOutSizeFactor: Int

  // ---- impl ----

  private[this] final var fft       : DoubleFFT_1D  = _
  private[this] final var fftBuf    : Array[Double] = _

  private[this] final var size      : Int  = _  // already multiplied by `fftInSizeFactor`
  private[this] final var padding   : Int  = _

  private[this] final var fftInOff      = 0  // regarding `fftBuf`
  private[this] final var fftInRemain   = 0
  private[this] final var fftOutOff     = 0  // regarding `fftBuf`
  private[this] final var fftOutRemain  = 0
  private[this] final var inOff         = 0  // regarding `bufIn`
  private[this] final var inRemain      = 0
  private[this] final var outOff        = 0  // regarding `bufOut`
  private[this] final var outRemain     = 0
  private[this] final var _fftSize      = 0  // refreshed as `size + padding`

  private[this] final var outSent       = true
  private[this] final var isNextFFT     = true

  override def postStop(): Unit = {
    super.postStop()
    fft = null
  }

  protected final def fftSize: Int = _fftSize

  @inline
  private[this] final def shouldRead    = inRemain     == 0 && canRead
  @inline
  private[this] final def canPrepareFFT = fftOutRemain == 0 && bufIn0 != null

  @tailrec
  protected final def process(): Unit = {
    // becomes `true` if state changes,
    // in that case we run this method again.
    var stateChange = false

    if (shouldRead) {
      readIns()
      inRemain    = bufIn0.size
      inOff       = 0
      stateChange = true
    }

    if (canPrepareFFT) {
      if (isNextFFT) {
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
        fftInOff    = 0
        fftInRemain = size
        isNextFFT   = false
        stateChange = true
      }

      val chunk     = math.min(fftInRemain, inRemain)
      val flushFFT  = inRemain == 0 && fftInOff > 0 && isClosed(shape.in0)
      if (chunk > 0 || flushFFT) {

        Util.copy(bufIn0.buf, inOff, fftBuf, fftInOff, chunk)
        inOff       += chunk
        inRemain    -= chunk
        fftInOff    += chunk
        fftInRemain -= chunk

        if (fftInOff == size || flushFFT) {
          Util.fill(fftBuf, fftInOff, fftBuf.length - fftInOff, 0.0)
          performFFT(fft, fftBuf)
          // fft.realForward(fftBuf)
          // Util.mul(fftBuf, 0, fftSize, 2.0 / fftSize) // scale correctly
          fftOutOff     = 0
          fftOutRemain  = _fftSize * fftOutSizeFactor
          isNextFFT     = true
        }

        stateChange = true
      }
    }

    if (fftOutRemain > 0) {
      if (outSent) {
        bufOut        = ctrl.borrowBufD()
        outRemain     = bufOut.size
        outOff        = 0
        outSent       = false
        stateChange   = true
      }

      val chunk = math.min(fftOutRemain, outRemain)
      if (chunk > 0) {
        Util.copy(fftBuf, fftOutOff, bufOut.buf, outOff, chunk)
        fftOutOff    += chunk
        fftOutRemain -= chunk
        outOff       += chunk
        outRemain    -= chunk
        stateChange   = true
      }
    }

    val flushOut = inRemain == 0 && fftInOff == 0 && fftOutRemain == 0 && isClosed(shape.in0)
    if (!outSent && (outRemain == 0 || flushOut) && isAvailable(shape.out)) {
      if (outOff > 0) {
        bufOut.size = outOff
        push(shape.out, bufOut)
      } else {
        bufOut.release()(ctrl)
      }
      bufOut      = null
      outSent     = true
      stateChange = true
    }

    if (flushOut && outSent) completeStage()
    else if (stateChange) process()
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

  protected def performFFT(fft: DoubleFFT_1D, fftBuf: Array[Double]): Unit = {
    fft.realInverse(fftBuf, false)
  }
}

final class Real1FullFFTStageImpl(ctrl: Control) extends FFTStageImpl {
  val name = "Real1FullFFT"

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Real1FullFFTLogicImpl(shape, ctrl)
}

final class Real1FullFFTLogicImpl(shape: FanInShape3[BufD, BufI, BufI, BufD], ctrl: Control)
  extends FFTLogicImpl(shape, ctrl) {

  protected val fftInSizeFactor  = 1
  protected val fftOutSizeFactor = 2

  protected def performFFT(fft: DoubleFFT_1D, fftBuf: Array[Double]): Unit = {
    fft.realForwardFull(fftBuf)
    Util.mul(fftBuf, 0, fftBuf.length, 2.0 / fftSize) // scale correctly
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
    var i = 1
    var j = 2
    while (j < fftBuf.length) {
      fftBuf(i) = fftBuf(j) * 0.5
      i += 1
      j += 2
    }
  }
}