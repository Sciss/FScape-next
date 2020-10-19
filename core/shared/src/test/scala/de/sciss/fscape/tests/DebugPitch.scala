//package de.sciss.fscape.tests
//
//import de.sciss.file._
//import de.sciss.fscape.gui.SimpleGUI
//import de.sciss.fscape.{GE, Graph, stream}
//import de.sciss.numbers.Implicits._
//import de.sciss.synth.io.AudioFile
//
//import scala.concurrent.Await
//import scala.concurrent.duration.Duration
//import scala.swing.Swing
//
//object DebugPitch extends App {
//  val fIn     = file("/data/projects/Maeanderungen/audio_work/raw_orf/HB_0_HH_T168.wav")
//  val specIn  = AudioFile.readSpec(fIn)
//  import specIn.sampleRate
//  println(specIn)
//
////  de.sciss.fscape.showStreamLog = true
//
//  def any2stringadd: Any = ()
//
//  lazy val g = Graph {
//    import de.sciss.fscape.graph._
//    val in0         = AudioFileIn(file = fIn, numChannels = 1)
//    val LIM = 8192 // 44100 * 4
//    val in = in0.take(LIM) // * 0.00000000001 + SinOsc(200.0/44100)
//    val numFrames   = math.min(LIM, specIn.numFrames)
//
//    val MinimumPitch        =  60.0 // 100.0
//    val MaximumPitch        = 300.0 // 1000.0
//    val VoicingThreshold    = 0.45
//    val SilenceThreshold    = 0.03
//    val OctaveCost          = 0.01
//    val OctaveJumpCost      = 0.35
//    val VoicedUnvoicedCost  = 0.14
//    val NumCandidatesM      = 14
//    val NumCandidates       = NumCandidatesM + 1
//
//    val minLag        = (sampleRate / MaximumPitch).floor.toInt
//    val maxLag        = (sampleRate / MinimumPitch).ceil .toInt
//    val numPeriods    = 3
//    val winSize       = maxLag * numPeriods
//    val winPadded     = (winSize * 1.5).ceil.toInt
//    val fftSize       = winPadded.nextPowerOfTwo
//    val fftSizeH: Int = fftSize/2
//
//    val stepSize    = winSize / 4 // fftSize / 4
//    val inSlid      = Sliding (in = in , size = winSize, step = stepSize)
//    val numSteps: Int = ((numFrames + stepSize - 1) / stepSize).toInt
//
//    val slidLen = numSteps * winSize
//
////    println(s"minLag $minLag, maxLag $maxLag, winSize $winSize, winPadded $winPadded, fftSize $fftSize, stepSize $stepSize, numSteps $numSteps")
////    Length(inSlid).poll(0, "inSlid-len")
//
//    def mkWindow(): GE = GenWindow(winSize, shape = GenWindow.Hann).take(slidLen)
//
//    val inLeak  = NormalizeWindow(inSlid, winSize, mode = NormalizeWindow.ZeroMean)
//    val inW     = inLeak * mkWindow()
//    val peaks0  = WindowApply(RunningMax(inLeak.abs, Metro(winSize)), winSize, winSize - 1)
//
//    def mkAR(sig: GE, normalize: Boolean = true): GE = {
//      val fft   = Real1FFT(in = sig, size = winSize, padding = fftSize - winSize, mode = 2)
//      val pow   = fft.complex.absSquared
//      val ar0   = Real1IFFT(pow, size = fftSize, mode = 2) // / fftSize
//      val ar1   = ResizeWindow(ar0, fftSize, stop = -fftSizeH)
//      if (!normalize) ar1 else NormalizeWindow(ar1, size = fftSizeH, mode = NormalizeWindow.Normalize)
//    }
//
//    import DebugThrough._
//
//    val r_a = mkAR(inW)
//    val r_w = mkAR(mkWindow())
//    val r_x = r_a / r_w
//
//    val paths = StrongestLocalMaxima(r_x /*<| "r_x"*/, size = (fftSizeH: GE) <| "fftSizeH",
//      minLag = minLag, maxLag = maxLag,
//      thresh = VoicingThreshold * 0.5, octaveCost = OctaveCost, num = NumCandidatesM)
//
//    val lags0       = paths.lags <| "lags0"
//    val strengths0  = paths.strengths
//    val lags        = BufferMemory(lags0      , fftSize * 4) <| "lags"
//    val strengths   = BufferMemory(strengths0 , fftSize * 4)
//    val peaks       = BufferMemory(peaks0     , fftSize * 2)  // WTF
////
//    val timeStepCorr        = 0.01 * sampleRate / stepSize    // 0.87 in this case
//    val octaveJumpCostC     = OctaveJumpCost      * timeStepCorr
//    val voicedUnvoicedCostC = VoicedUnvoicedCost  * timeStepCorr
//
////    Length(r_a)       .poll(0, "r_a-len")
////    Length(r_x)       .poll(0, "r_x-len")
//    Length(lags)      .poll(0, "lags-len")
////    Length(strengths) .poll(0, "strengths-len")
////    Length(peaks)     .poll(0, "peaks-len")
//
//    val vitIn     = PitchesToViterbi(lags = lags, strengths = strengths, numIn = NumCandidatesM,
//      peaks = peaks, maxLag = maxLag,
//      voicingThresh = VoicingThreshold, silenceThresh = SilenceThreshold, octaveCost = OctaveCost,
//      octaveJumpCost = octaveJumpCostC, voicedUnvoicedCost = voicedUnvoicedCostC)
//
//    Length(vitIn).poll(0, "vitIn-len")
//
////    val states    = Viterbi(add = vitIn, numStates = NumCandidates)
////
////    val lagsSel   = WindowApply(BufferMemory(lags, numSteps * NumCandidates), size = NumCandidatesM, index = states,
////      mode = 3)
////    val hasFreq   = lagsSel > 0
////    val freqsSel  = Gate(lagsSel.reciprocal, hasFreq) * sampleRate
////
////    val data = freqsSel
////
////    RepeatWindow(data).poll(Metro(2), "data")
//  }
//
//  val config = stream.Control.Config()
//  config.useAsync = false
//  config.blockSize  = 512 // 4096
//  implicit val ctrl: stream.Control = stream.Control(config)
//  ctrl.run(g)
//
//  println("Running.")
//
//  Swing.onEDT {
//    SimpleGUI(ctrl)
//  }
//
//  Await.result(ctrl.status, Duration.Inf)
//}
