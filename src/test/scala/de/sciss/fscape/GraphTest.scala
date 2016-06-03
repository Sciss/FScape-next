package de.sciss.fscape

import de.sciss.file._
import de.sciss.synth.io.AudioFileSpec

object GraphTest extends App {
  val fIn   = userHome / "Documents" / "projects" / "Unlike" / "audio_work" / "mentasm-e8646341-63dcf8a8.aif"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"

  lazy val g = Graph {
    import graph._

    // 'analysis'
    val in          = DiskIn(file = fIn, numChannels = 1)
    val fftSize     = 131072
    val winStep     = fftSize / 4
    val inW         = Sliding       (in = in , size = fftSize, step = winStep)
    val fft         = Real1FullFFT  (in = inW, size = fftSize)

    // 'percussion'
    val logC        = ComplexUnaryOp(in = fft , op = ComplexUnaryOp.Log).max(-80)
    val cep         = Complex1IFFT  (in  = logC, size = fftSize) / fftSize

    val coefs       = Vector(CepCoef.One, CepCoef.Two)
    val sig         = coefs.map { coef =>
      import coef._
      val cepOut      = FoldCepstrum  (in = cep, size = fftSize,
        crr = crr, cri = cri, clr = clr, cli = cli,
        ccr = ccr, cci = cci, car = car, cai = cai)
      val freq        = Complex1FFT   (in = cepOut, size = fftSize) * fftSize
      val fftOut      = ComplexUnaryOp(in = freq  , op = ComplexUnaryOp.Exp)

      // 'synthesis'
      val outW        = Real1FullIFFT(in = fftOut, size = fftSize) * gain

      val winIn       = GenWindow(size = fftSize, shape = GenWindow.Hann)
      val winOut      = outW * winIn
      val lap         = OverlapAdd(winOut, size = fftSize, step = winStep)
      lap
    }

    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = sig.size, sampleRate = 44100), in = sig)
  }
}