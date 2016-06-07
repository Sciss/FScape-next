package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.numbers
import de.sciss.synth.io.AudioFileSpec

import scala.swing.Swing

object PercussionTest extends App {
  val fIn   = userHome / "Documents" / "projects" / "Unlike" / "audio_work" / "mentasm-e8646341-63dcf8a8.aif"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"

  import graph._

  def normalize(in: GE): GE = {
    val max       = RunningMax(in.abs).last
    max.ampdb.poll(0, "max [dB]")
    import numbers.Implicits._
    val headroom  = -0.2.dbamp
    val gain      = max.reciprocal * headroom
    val buf       = BufferDisk(in)
    val sig       = buf * gain
    sig
  }

  lazy val gABBREV = Graph {
    // 'analysis'
    val in          = DiskIn(file = fIn, numChannels = 1)
    val fftSize     = 131072
    val winStep     = fftSize / 4
    val inW         = Sliding         (in = in , size = fftSize, step = winStep)
    val fft         = Real1FullFFT    (in = inW, size = fftSize)

    // 'percussion'
    val logC        = fft  // ComplexUnaryOp  (in = fft , op = ComplexUnaryOp.Log).max(-80)
    val cep         = logC // Complex1IFFT    (in = logC, size = fftSize) / fftSize

    val coefs       = Vector(CepCoef.One) // , CepCoef.Two)
    val sig         = coefs.map { coef =>
        val cepOut      = cep //
        // FoldCepstrum  (in = cep, size = fftSize,
        //  crr = crr, cri = cri, clr = clr, cli = cli,
        //  ccr = ccr, cci = cci, car = car, cai = cai)
        val freq        = cepOut // Complex1FFT   (in = cepOut, size = fftSize) * fftSize
        val fftOut      = freq // ComplexUnaryOp(in = freq, op = ComplexUnaryOp.Exp)

        // 'synthesis'
        val outW        = Real1FullIFFT (in = fftOut, size = fftSize) // * gain

        // val winIn       = GenWindow(size = fftSize, shape = GenWindow.Hann)
        val winOut      = outW // * winIn
        val lap         = OverlapAdd(in = winOut, size = fftSize, step = winStep)

        normalize(lap)
      }

    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = sig.size, sampleRate = 44100), in = sig)
  }

  lazy val g = Graph {
    // 'analysis'
    val in          = DiskIn(file = fIn, numChannels = 1)
    val fftSize     = 131072
    val winStep     = fftSize / 4
    val inW         = Sliding         (in = in , size = fftSize, step = winStep)
    val fft         = Real1FullFFT    (in = inW, size = fftSize)

    // 'percussion'
    val logC        = ComplexUnaryOp  (in = fft , op = ComplexUnaryOp.Log).max(-80)
    val cep         = Complex1IFFT    (in = logC, size = fftSize) / fftSize

    val coefs       = Vector(CepCoef.One) // , CepCoef.Two)
    val sig         = coefs.map { coef =>
      import coef._
      val cepOut      = FoldCepstrum  (in = cep, size = fftSize,
        crr = crr, cri = cri, clr = clr, cli = cli,
        ccr = ccr, cci = cci, car = car, cai = cai)
      val freq        = Complex1FFT   (in = cepOut, size = fftSize) * fftSize
      val fftOut      = ComplexUnaryOp(in = freq, op = ComplexUnaryOp.Exp)

      // 'synthesis'
      val outW        = Real1FullIFFT (in = fftOut, size = fftSize) // * gain

      val winIn       = GenWindow(size = fftSize, shape = GenWindow.Hann)
      val winOut      = outW * winIn
      val lap         = OverlapAdd(in = winOut, size = fftSize, step = winStep)

      normalize(lap)
    }

    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = sig.size, sampleRate = 44100), in = sig)
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