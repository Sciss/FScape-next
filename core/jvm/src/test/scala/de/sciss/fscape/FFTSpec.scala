package de.sciss.fscape

import de.sciss.fscape.Ops._
import de.sciss.kollflitz.Vec
import de.sciss.numbers

import scala.concurrent.Promise

class FFTSpec extends UGenSpec {

  "The real-valued FFT UGens" should "work as specified" in {
    val fftSizeHs = List(
      16, 512, 1024, 4096
    )

    for {
      fftSizeH  <- fftSizeHs
      full      <- Seq(false, true)
    } {
      val rmsInP    = Promise[Vec[Double]]()
      val rmsFreqP  = Promise[Vec[Double]]()
      val rmsOutP   = Promise[Vec[Double]]()
      val magP      = Promise[Vec[Double]]()
      val phaseP    = Promise[Vec[Double]]()
      val sigOutP   = Promise[Vec[Double]]()
      val rmsExp    = math.sqrt(0.5)
      val fftSize   = fftSizeH << 1
      val period    = fftSize / 2
      val phase0    = PiH

      val g = Graph {
        import graph._

        val sig  = SinOsc(1.0 / period, phase = phase0).take(fftSize)

        def rms(sig: GE, p: Promise[Vec[Double]]): Unit = {
          val v = (sig.squared.sum / fftSize).sqrt // .ampDb
          DebugDoublePromise(v, p)
        }

        rms(sig, rmsInP)

        val (fft, magS) = if (full) {
          val _fft    = Real1FullFFT(sig, fftSize)
          val _mag    = _fft.complex.mag * math.sqrt(fftSize) * 0.5
          (_fft, _mag)
        } else {
          val _fft    = Real1FFT(sig, fftSize, mode = 1)
          val _mag    = _fft.complex.mag * math.sqrt(fftSizeH)
          (_fft, _mag)
        }

        rms(magS, rmsFreqP)
        val mag   = fft.complex.mag
        val phase = fft.complex.phase

        val ifft = if (full) {
          Real1FullIFFT(fft, fftSize)
        } else {
          Real1IFFT(fft, fftSize, mode = 1)
        }

//        Plot1D(ifft, fftSize)
        rms(ifft, rmsOutP)

        DebugDoublePromise(ifft , sigOutP )
        DebugDoublePromise(mag  , magP    )
        DebugDoublePromise(phase, phaseP  )
      }

      runGraph(g)

      val resRmsIn  : Double = getPromise(rmsInP  )
      val resRmsFreq: Double = getPromise(rmsFreqP)
      val resRmsOut : Double = getPromise(rmsOutP )
      assert (resRmsIn    === (rmsExp +- eps))
      assert (resRmsFreq  === (rmsExp +- eps))
      assert (resRmsOut   === (rmsExp +- eps))

      val resOut  = getPromiseVec(sigOutP)
      val expOut  = Vector.tabulate(fftSize)(idx => math.sin(idx * Pi2 / period + phase0))

      val resMag  = getPromiseVec(magP)
      val expMag  = Vector.tabulate(if (full) fftSize else fftSizeH + 1) { idx =>
        if (idx == 2 || idx == fftSize - 2) 1.0 else 0.0
      }
      val resPhaseSq  = getPromiseVec(phaseP)
      val resPhase    = resPhaseSq(2)

//      println(resMag.mkString("GOT: ", ", ", ""))
//      println(expOut.mkString("EXP: ", ", ", ""))

      difOk(resOut, expOut)
      difOk(resMag, expMag)

      assert (resPhase === (0.0 +- eps))
    }
  }

  they should "allow fft-size modulation" in {
    // reconstruction works for odd fft sizes,
    // but obviously something is weird in the spectrum, as we don't have
    // an integer number of complex re/im pairs
    val fftSizes = Vector(
      16, 32, 48, 58 /*57*/, 510 /*511*/, 512, 514 /*513*/, 1022 /*1023*/, 1024, 1026 /*1025*/
    )

    val sigLen    = fftSizes.sum
    val phaseP    = Promise[Vec[Double]]()
    val sigOutP   = Promise[Vec[Double]]()

    val g = Graph {
      import graph._

      val fftSizesGE: GE = fftSizes.map(x => x: GE).reduce(_ ++ _)
      val sig     = DelayN(Metro(fftSizesGE).take(sigLen - 1), 1, 1)
      val fft     = Real1FFT(sig, size = fftSizesGE, mode = 1)
      val phase   = fft.complex.phase
      val ifft    = Real1IFFT(fft, size = fftSizesGE, mode = 1)

      DebugDoublePromise(ifft , sigOutP )
      DebugDoublePromise(phase, phaseP  )
    }

    runGraph(g)

    val resOut  = getPromiseVec(sigOutP)
    val expOut  = (0.0 +: fftSizes.flatMap(n => Vector.tabulate(n)(i => if (i == 0) 1.0 else 0.0))).init

    val resPhase  = getPromiseVec(phaseP)
    import numbers.Implicits._
    val expPhase  = fftSizes.flatMap(n => Vector.tabulate((n + 2)/2)(_.linLin(0, n, 0, -Pi2)))

    assert (resOut.size === sigLen)

    difOk       (resOut  , expOut  )
    difRadiansOk(resPhase, expPhase)

//    assert (resPhase === (0.0 +- eps))
  }

  "The complex-valued FFT UGens" should "work as specified" in {
    val reOutP    = Promise[Vec[Double]]()
    val imOutP    = Promise[Vec[Double]]()
    val magP      = Promise[Vec[Double]]()
    val phaseP    = Promise[Vec[Double]]()
    val peakBandP = Promise[Vec[Int]]()
    val fftSizeH  = 1024
    val fftSize   = fftSizeH << 1
    val div       = 4
    val period    = fftSize / div

    val g = Graph {
      import graph._

      val sin       = SinOsc(1.0 / period, phase = 0.0  ).take(fftSize)
      val cos       = SinOsc(1.0 / period, phase = PiH  ).take(fftSize)
      val sig       = sin zip cos
      val fft       = Complex1FFT(sig, fftSize)
      val mag       = fft.complex.mag
      val phase     = fft.complex.phase
      val ifft      = Complex1IFFT(fft, fftSize)
      val reOut     = ifft.complex.real
      val imOut     = ifft.complex.imag
      val peakBand  = WindowMaxIndex(mag  , fftSize)
      val peakPhase = WindowApply   (phase, fftSize, peakBand)

      DebugDoublePromise(reOut, reOutP  )
      DebugDoublePromise(imOut, imOutP  )
      DebugDoublePromise(mag  , magP    )
      DebugDoublePromise(peakPhase, phaseP  )
      DebugIntPromise   (peakBand, peakBandP)
    }

    runGraph(g)

    val resRe   = getPromiseVec(reOutP)
    val resIm   = getPromiseVec(imOutP)
    val expRe   = Vector.tabulate(fftSize)(idx => math.sin(idx * Pi2 / period + 0.0))
    val expIm   = Vector.tabulate(fftSize)(idx => math.sin(idx * Pi2 / period + PiH))

    val resPhase    = getPromise(phaseP)
    val peakBand    = getPromise(peakBandP)

    difOk(resRe, expRe)
    difOk(resIm, expIm)

    assert (peakBand === fftSize - div)
    assert (resPhase === PiH +- eps)
  }
}