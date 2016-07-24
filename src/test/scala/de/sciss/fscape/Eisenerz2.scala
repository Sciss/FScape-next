package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.numbers
import de.sciss.synth.io.{AudioFile, AudioFileSpec, AudioFileType, SampleFormat}

import scala.swing.Swing

object Eisenerz2 extends App {
  val Side = 'L'

  require (Side == 'L' || Side == 'R')

  val fIn       = userHome / "Documents" / "projects" / "Eisenerz" / "audio_work" / s"Muehle160718_1739Ed-${Side}Hp.aif"
  val fOut1     = fIn.parent / s"Muehle-${Side}Perc1.w64"
  val fOut2     = fIn.parent / s"Muehle-${Side}Perc2.w64"
  val fOutTest  = fIn.parent / s"Muehle-${Side}Perc-Test2.w64"

//  val FRAME1 = (1867776 - FRAME0)/2 + FRAME0
  val HOURS   = 6
  val DECIM   = 32
  lazy val fInLen = AudioFile.readSpec(fIn).numFrames

//  val FRAME0  = 1185280
//  val FRAME1  = ((HOURS * 60L * 60 * 44100) / DECIM).toInt + FRAME0
  val FRAME1  = fInLen.toInt
  val FRAME0  = FRAME1 - ((HOURS * 60L * 60 * 44100) / DECIM).toInt

//  require(!fOut1.exists)
//  require(!fOut2.exists)

  import graph._
  import numbers.Implicits._

  lazy val g = Graph {
    // 'analysis'
    val in0         = DiskIn(file = fIn, numChannels = 1)
    val in          = in0.drop(FRAME0).take(FRAME1 - FRAME0)
    val fftSize     = 131072 * 4
    val overlapNum  = 2
    val winStepOut  = fftSize / overlapNum
    //    val winStepIn   = fftSize / 16
//    val winStepOut  = fftSize / 4
    val winStepIn   = fftSize / (overlapNum * DECIM)
    val inW         = Sliding         (in = in , size = fftSize, step = winStepIn)
    val fft         = Real1FullFFT    (in = inW, size = fftSize)

    // 'percussion'
    val logC        = ComplexUnaryOp  (in = fft , op = ComplexUnaryOp.Log).max(-80)
    val cep         = Complex1IFFT    (in = logC, size = fftSize) / fftSize
//    val coefs       = Vector(CepCoef.One, CepCoef.Two)
    val coefs       = Vector(CepCoef.Two)
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

    val sig = lap // normalize(lap)
    DiskOut(fOutTest, AudioFileSpec(fileType = AudioFileType.Wave64, sampleFormat = SampleFormat.Float,
      numChannels = 1, sampleRate = 44100), in = ChannelProxy(sig, 0))
//    DiskOut(fOut1, AudioFileSpec(fileType = AudioFileType.Wave64, sampleFormat = SampleFormat.Float,
//      numChannels = 1, sampleRate = 44100), in = ChannelProxy(sig, 0))
//    DiskOut(fOut2, AudioFileSpec(fileType = AudioFileType.Wave64, sampleFormat = SampleFormat.Float,
//      numChannels = 1, sampleRate = 44100), in = ChannelProxy(sig, 1))
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