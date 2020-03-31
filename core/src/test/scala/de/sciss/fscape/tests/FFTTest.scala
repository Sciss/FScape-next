package de.sciss.fscape.tests

import de.sciss.fscape.{GE, Graph, graph, stream}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

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
    println(s"sigLen = $sigLen")
    val fftSizesGE: GE = fftSizes.map(x => x: GE).reduce(_ ++ _)
    val sig0    = DelayN(Metro(fftSizesGE).take(sigLen), 1, 1).init
    val sig     = sig0 // * (Frames(sig0) > 20) * (Frames(sig0) < 90)
//    Plot1D(sig, sigLen, "in")
    val fft     = Real1FFT(sig, size = fftSizesGE, mode = 1)
//    val fft     = Real1FullFFT(sig, size = fftSizesGE)
//    val mag     = fft.complex.mag
//    Plot1D(mag * 32, 48, "mag")
    val phase   = fft.complex.phase
    val ifft    = Real1IFFT(fft, size = fftSizesGE, mode = 1)
//    val ifft    = Real1FullIFFT(fft, size = fftSizesGE)
    Length(phase).poll("phase.length")
    Length(ifft ).poll("ifft.length")
    Plot1D(ifft, sigLen, "out")
  }

  val ctrl = stream.Control()

//  scala.swing.Swing.onEDT {
//    de.sciss.fscape.gui.SimpleGUI(ctrl)
//  }

  ctrl.run(g)

  Await.result(ctrl.status, Duration.Inf)
}