package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control
import de.sciss.fscape.{GE, Graph, graph}
import de.sciss.numbers.Implicits._
import de.sciss.synth.io.AudioFileSpec

import scala.swing.Swing

object NormalizeTest2 extends App {
  val fIn   = userHome / "Documents" / "projects" / "Imperfect" / "anemone" / "rec" / "capture.aif"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"

  val g = Graph {
    import graph._
//    val in = AudioFileIn("file-in")
    val in0 = AudioFileIn(fIn, numChannels = 2)
    val in = ChannelProxy(in0, 0)

    // 'analysis'
    val fftSize     = 131072
    val winStep     = fftSize / 4
    val inW         = Sliding         (in = in , size = fftSize, step = winStep)
    val fft         = Real1FullFFT    (in = inW, size = fftSize)

    // (fft \ 0).poll(Metro(44100), "fft-in")

    val One = CepCoef(
      crr =  0, cri =  0,
      clr = +1, cli = +1,
      ccr = +1, cci = -1,
      car = +1, cai = -1,
      gain = 1.0/2097152    // XXX TODO --- what's this factor?
    )

    // 'percussion'
    val logC        = fft.complex.log.max(-80)
    val cep         = Complex1IFFT    (in = logC, size = fftSize) / fftSize
    val coef = One
    val cepOut = {
      import coef._
      FoldCepstrum  (in = cep, size = fftSize,
        crr = crr, cri = cri, clr = clr, cli = cli,
        ccr = ccr, cci = cci, car = car, cai = cai)
    }
    val freq        = Complex1FFT   (in = cepOut, size = fftSize) * fftSize
    val fftOut      = freq.complex.exp

    // (fftOut \ 0).poll(Metro(44100), "fft-out")

    // 'synthesis'
    val outW        = Real1FullIFFT (in = fftOut, size = fftSize)
    val winIn       = GenWindow(size = fftSize, shape = GenWindow.Hann)
    val winOut      = outW * winIn
    val lap         = OverlapAdd(in = winOut, size = fftSize, step = winStep)

    // (lap \ 0).poll(Metro(44100), "lap")

    def normalize(in: GE): GE = {
      val abs       = in.abs
      val run       = RunningMax(abs)
      ChannelProxy(run, 0).poll(Metro(44100), "run-max")
      Length(run).poll(0, "run-len")
      val max       = run.last
      max.ampDb.poll(0, "max [dB]")
      val headroom  = -0.2.dbAmp
      val gain      = max.reciprocal * headroom
      val buf       = BufferDisk(in)
      val sig       = buf * gain
      ChannelProxy(sig, 0).poll(Metro(44100), "norm")
      sig
    }

    // Progress()
    val sig    = normalize(lap)
    val sigLen = Length(lap)
    val out    = AudioFileOut(file = fOut, spec = AudioFileSpec(numChannels = 1 /* 2 */, sampleRate = 44100), in = sig)
//    val out = AudioFileOut("file-out", in = sig)
    Progress(out / (2 * sigLen), Metro(44100), "normalize")
  }


  val config = Control.Config()
  config.useAsync = false
  implicit val ctrl: Control = Control(config)
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}
