package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.numbers
import de.sciss.synth.io.{AudioFileSpec, AudioFileType, SampleFormat}

import scala.swing.Swing

object EisenerzMuehle extends App {
//  val fIn   = userHome / "Documents" / "projects" / "Unlike" / "audio_work" / "mentasm-e8646341-63dcf8a8.aif"
  val fIn   = userHome / "Documents" / "projects" / "Eisenerz" / "audio_work" / "Muehle160718_1739Ed-LHpAmp.aif"
//  val fOut  = userHome / "Music" / "work" / "_killme.aif"
  val fOut1 = fIn.parent / "Muehle-LPerc1.w64"
  val fOut2 = fIn.parent / "Muehle-LPerc2.w64"

  import graph._
  import numbers.Implicits._

  lazy val g = Graph {
    // 'analysis'
    val in          = DiskIn(file = fIn, numChannels = 1)
    val fftSize     = 131072
    val winStepIn   = fftSize / 16
    val winStepOut  = fftSize / 4
    val inW         = Sliding         (in = in , size = fftSize, step = winStepIn)
    val fft         = Real1FullFFT    (in = inW, size = fftSize)

    // 'percussion'
    val logC        = ComplexUnaryOp  (in = fft , op = ComplexUnaryOp.Log).max(-80)
    val cep         = Complex1IFFT    (in = logC, size = fftSize) / fftSize
    val coefs       = Vector(CepCoef.One, CepCoef.Two)
    val cepOut = coefs.map { coef =>
      import coef._
      FoldCepstrum(in = cep, size = fftSize,
        crr = crr, cri = cri, clr = clr, cli = cli,
        ccr = ccr, cci = cci, car = car, cai = cai)
    }
    val freq        = Complex1FFT   (in = cepOut, size = fftSize) * fftSize
    val fftOut      = ComplexUnaryOp(in = freq, op = ComplexUnaryOp.Exp)

    // 'synthesis'
    val outW        = Real1FullIFFT (in = fftOut, size = fftSize)
    val winIn       = GenWindow(size = fftSize, shape = GenWindow.Hann)
    val winOut      = outW * winIn
    val lap         = OverlapAdd(in = winOut, size = fftSize, step = winStepOut)

    val sig = normalize(lap)
    DiskOut(fOut1, AudioFileSpec(fileType = AudioFileType.Wave64, sampleFormat = SampleFormat.Float,
      numChannels = 1, sampleRate = 44100), in = ChannelProxy(sig, 0))
    DiskOut(fOut2, AudioFileSpec(fileType = AudioFileType.Wave64, sampleFormat = SampleFormat.Float,
      numChannels = 1, sampleRate = 44100), in = ChannelProxy(sig, 1))
  }

  def normalize(in: GE): GE = {
    val max       = RunningMax(in.abs).last
    max.ampdb.poll(0, "max [dB]")
    val headroom  = -0.2.dbamp
    val gain      = max.reciprocal * headroom
    val buf       = BufferDisk(in)
    val sig       = buf * gain
    sig
  }

  val config = stream.Control.Config()
  config.useAsync = false
  implicit val ctrl = stream.Control(config)
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}