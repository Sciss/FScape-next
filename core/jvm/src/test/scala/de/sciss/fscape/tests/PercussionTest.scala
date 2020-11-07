package de.sciss.fscape
package tests

import de.sciss.audiofile.{AudioFile, AudioFileSpec}
import de.sciss.file._
import de.sciss.fscape.Ops._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.numbers

import scala.swing.Swing

object PercussionTest extends App {
  val fIn   = file("/") / "data" / "projects" / "Unlike_SpillOver" / "audio_work" / "mentasm-e8646341-63dcf8a8.aif"
  val fOut  = file("/") / "data" / "temp" / "_killme.aif"

  import graph._
  import numbers.Implicits._

  val specIn = AudioFile.readSpec(fIn)

  lazy val g = Graph {
    // 'analysis'
    val in          = AudioFileIn(file = fIn.toURI, numChannels = 1)
    val fftSize     = 131072
    val winStep     = fftSize / 4
    val inW         = Sliding         (in = in , size = fftSize, step = winStep)
    val fft         = Real1FullFFT    (in = inW, size = fftSize)

    // 'percussion'
    val logC        = fft.complex.log /* ComplexUnaryOp  (in = fft , op = ComplexUnaryOp.Log.id) */.max(-80)
    val cep         = Complex1IFFT    (in = logC, size = fftSize) / fftSize
    val coefs       = Vector(CepCoef.One, CepCoef.Two)
    val cepOut      = coefs.map { coef =>
      import coef._
      FoldCepstrum  (in = cep, size = fftSize,
        crr = crr, cri = cri, clr = clr, cli = cli,
        ccr = ccr, cci = cci, car = car, cai = cai)
    }
    val freq        = Complex1FFT   (in = cepOut, size = fftSize) * fftSize
    val fftOut      = freq.complex.exp // ComplexUnaryOp(in = freq, op = ComplexUnaryOp.Exp.id)

    // 'synthesis'
    val outW        = Real1FullIFFT (in = fftOut, size = fftSize)
    val winIn       = GenWindow(size = fftSize, shape = GenWindow.Hann)
    val winOut      = outW * winIn
    val lap         = OverlapAdd(in = winOut, size = fftSize, step = winStep).take(specIn.numFrames)

    val sig = normalize(lap)
    AudioFileOut(file = fOut.toURI, spec = AudioFileSpec(numChannels = coefs.size, sampleRate = 44100), in = sig)
  }

  def normalize(in: GE): GE = {
    val max       = RunningMax(in.abs).last
    max.ampDb.poll(0, "max [dB]")
    val headroom  = -0.2.dbAmp
    val gain      = max.reciprocal * headroom
    val buf       = BufferDisk(in)
    val sig       = buf * gain
    sig
  }

  val config = stream.Control.Config()
  config.useAsync = false
  implicit val ctrl: stream.Control = stream.Control(config)
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}