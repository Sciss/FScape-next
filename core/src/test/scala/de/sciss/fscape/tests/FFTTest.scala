package de.sciss.fscape.tests

import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.{GE, Graph, graph, stream}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.swing.Swing

object FFTTest extends App {
  lazy val g1 = Graph {
    import graph._
    val fftSizeH  = 512
    val fftSize = fftSizeH << 1
    val period  = 16 // fftSize / 4
    val sig     = SinOsc(1.0 / period, phase = math.Pi/2).take(fftSize)

    def rms(sig: GE, label: String): Unit = {
      val v = (sig.squared.sum / fftSize).sqrt.ampDb
      v.poll(s"$label [dB]")
    }

    rms(sig, "in")

//    val sig = DelayN(Impulse(0), fftSize-1, fftSize-1)
//    Plot1D(sig, fftSize, "sig")
//    val fft     = Real1FFT(sig, fftSize, mode = 1)
//    val mag     = fft.complex.mag * math.sqrt(fftSizeH)
    val fft     = Real1FullFFT(sig, fftSize)
    val mag     = fft.complex.mag * math.sqrt(fftSize) * 0.5

    rms(mag, "fft")


//    Plot1D(fft.complex.mag  * fftSizeH, fftSizeH, "mag")
//    Plot1D(fft.complex.phase, fftSizeH, "phase")
//    val ifft    = Real1IFFT(fft, fftSize, mode = 1)
    val ifft    = Real1FullIFFT(fft, fftSize)

    rms(ifft, "out")

//    Plot1D(ifft, fftSize, "reconstructed")
  }

  lazy val g = Graph {
    import graph._
    val fftSizes = Vector(
      16, 32, 48, 57, 511, 512, 513, 1023, 1024, 1025
    )
    val sigLen    = fftSizes.sum
    val fftSizesGE: GE = fftSizes.map(x => x: GE).reduce(_ ++ _)
    val sig     = DelayN(Metro(fftSizesGE).take(sigLen), 1, 1)
    val fft     = Real1FFT(sig, size = fftSizesGE, mode = 1)
    val phase   = fft.complex.phase
    val ifft    = Real1IFFT(fft, size = fftSizesGE, mode = 1)
    Length(phase).poll("phase.length")
    Length(ifft ).poll("ifft.length")
  }

  val ctrl = stream.Control()

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  ctrl.run(g)

  Await.result(ctrl.status, Duration.Inf)
}