package de.sciss.fscape

import de.sciss.file._
import de.sciss.synth.io.AudioFileSpec

object Test extends App {
  val fIn   = userHome / "Music" / "work" / "mentasm-199a3aa1.aif"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"

  val g = Graph {
    import graph._
    ???
//    val in          = DiskIn(file = fIn, numChannels = 1)
//    val fftSize     = 131072 // 32768 // 8192
//    val winStep     = fftSize / 4
//    val inW         = Sliding       (in = in , size = fftSize, step    = winStep)
//    val fft0        = Real1FullFFT  (in = inW, size = fftSize)
//    val fft         = fft0 // ComplexUnaryOp(in = fft0, op = ComplexUnaryOp.Conj)
//
//    // 'percussion'
//    val log         = ComplexUnaryOp(in = fft , op = ComplexUnaryOp.Log)
//    val logC        = BinaryOp      (in1 = log , in2  = const(-80), op = BinaryOp.Max)
//    val cep0        = Complex1IFFT  (in  = logC, size = const(fftSize), padding = const(0))
//    val cep1        = BinaryOp      (in1 = cep0, in2  = const(1.0/fftSize), op = BinaryOp.Times)
//
//    val coefs       = Vector(Coef1, Coef2)
//    val cepB        = BroadcastBuf(in = cep1, numOutputs = 2)
//    val sig         = (coefs zip cepB).map { case (coef, cep) =>
//      import coef._
//      val cepOut      = FoldCepstrum  (in = cep, size = const(fftSize),
//        crr = const(crr), cri = const(cri), clr = const(clr), cli = const(cli),
//        ccr = const(ccr), cci = const(cci), car = const(car), cai = const(cai))
//      val freq0       = Complex1FFT   (in = cepOut, size = const(fftSize), padding = const(0))
//      val freq1       = BinaryOp      (in1 = freq0, in2 = const(fftSize), op = BinaryOp.Times)
//      val freq        = freq1 // ComplexUnaryOp(in = freq1, op = ComplexUnaryOp.Conj)
//    val fftOut      = ComplexUnaryOp(in = freq  , op = ComplexUnaryOp.Exp)
//
//      // 'synthesis'
//      val outW        = Real1FullIFFT(in = fftOut, size = const(fftSize), padding = const(0))
//
//      val times       = BinaryOp(in1 = outW, in2 = const(gain), op = BinaryOp.Times)
//      val winIn       = GenWindow(size = const(fftSize), shape = const(GenWindow.Hann.id), param = const(0.0))
//      val winOut      = BinaryOp(in1 = times, in2 = winIn, op = BinaryOp.Times)
//      val lap         = OverlapAdd(winOut, size = const(fftSize), step = const(winStep))
//      lap
//    }
//
//    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = sig.size, sampleRate = 44100), in = sig)
  }

  val process = g.expand
  process.run()
}
