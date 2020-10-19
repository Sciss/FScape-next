package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.{GE, Graph, stream}
import de.sciss.numbers.Implicits._
import de.sciss.synth.io.AudioFile

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/*

  Implementation of Boersma's algorithm

  cf. Paul Boersma, ACCURATE SHORT-TERM ANALYSIS OF THE FUNDAMENTAL FREQUENCY AND
  THE HARMONICS-TO-NOISE RATIO OF A SAMPLED SOUND,
  Institute of Phonetic Sciences, University of Amsterdam, Proceedings 17 (1993), 97-110

 */
object PitchTest extends App {
  val fIn     = file("/data/projects/Maeanderungen/audio_work/raw_orf/HB_0_HH_T168.wav")
//  val fIn     = file("/data/temp/ac-in.aif")
//  val fIn     = file("/data/temp/ac-inFOO.aif")
//  val fOut    = file("/data/temp/test.aif")
  val specIn  = AudioFile.readSpec(fIn)
  import specIn.sampleRate

  def any2stringadd: Any = ()

  lazy val g = Graph {
    import de.sciss.fscape.graph._
//    val start       = 19932
//    val numFrames   = 200L * 2000 // 242239 // 48000 * 87 // specIn.numFrames
//    val in          = AudioFileIn(file = fIn, numChannels = 1).drop(start).take(numFrames)
    val in0         = AudioFileIn(file = fIn, numChannels = 1)
//    val in0         = AudioFileIn(file = fIn, numChannels = 1).take(numFrames)
    val in = in0 // * 0.00000000001 + SinOsc(200.0/44100)
    val numFrames   = specIn.numFrames

    val MinimumPitch        =  60.0 // 100.0
    val MaximumPitch        = 300.0 // 1000.0
    val VoicingThreshold    = 0.45
    val SilenceThreshold    = 0.03
    val OctaveCost          = 0.01
    val OctaveJumpCost      = 0.35
    val VoicedUnvoicedCost  = 0.14
    val NumCandidatesM      = 14
    val NumCandidates       = NumCandidatesM + 1

    val minLag        = (sampleRate / MaximumPitch).floor.toInt
    val maxLag        = (sampleRate / MinimumPitch).ceil .toInt
    val numPeriods    = 3
    val winSize       = maxLag * numPeriods
    val winPadded     = (winSize * 1.5).ceil.toInt
    val fftSize       = winPadded.nextPowerOfTwo
    val fftSizeH: Int = fftSize/2

    //    val fftSize     = 4096 // 2048  // c. 40 ms
    val stepSize    = winSize / 4 // fftSize / 4
    val inSlid      = Sliding (in = in , size = winSize, step = stepSize)
    val numSteps: Int = ((numFrames + stepSize - 1) / stepSize).toInt
    val slidLen = numSteps * winSize

    println(s"minLag $minLag, maxLag $maxLag, winSize $winSize, winPadded $winPadded, fftSize $fftSize, stepSize $stepSize, numSteps $numSteps")

    def mkWindow(): GE = GenWindow.Hann(winSize).take(slidLen)

    val inLeak  = NormalizeWindow.zeroMean(inSlid, winSize)
    val inW     = inLeak * mkWindow()
    val peaks0  = WindowApply(RunningMax(inLeak.abs, Metro(winSize)), winSize, winSize - 1)
//    RepeatWindow(peaks).poll(Metro(2), "peak")

    def mkAR(sig: GE, normalize: Boolean = true) = {
      val fft   = Real1FFT(in = sig, size = winSize, padding = fftSize - winSize, mode = 2)
      val pow   = fft.complex.absSquared
      val ar0   = Real1IFFT(pow, size = fftSize, mode = 2) // / fftSize
      val ar1   = ResizeWindow(ar0, fftSize, stop = -fftSizeH)
      //      val ar0B  = BufferMemory(ar0, fftSize)
      //      val c0    = WindowApply(ar0B, size = fftSize, index = 0)
      //      val c0W   = RepeatWindow(c0, num = fftSize)
      //      ar0B / c0W
      if (!normalize) ar1 else NormalizeWindow.normalize(ar1, size = fftSizeH)
    }

    // val localPeak = inW.abs.max
    // val isSilent = localPeak < SilenceThreshold

    val r_a = mkAR(inW) // , normalize = false)
    val r_w = mkAR(mkWindow())
    val r_x = r_a / r_w

    //    r_a.poll(0, "arX[0]")
//    r_a.poll(fftSizeH, "arX")
//    r_x.poll(fftSizeH, "arX")
    //    r_w.poll(0, "arW[0]")
    //    RunningMax(inW.abs).poll(DelayN(Metro(0), winSize - 1, winSize - 1), "localAbsPeak")
    // (ConstantD(0) ++ RunningMax(inW.abs, Metro(winSize))).poll(Metro(winSize) - Metro(0), "localAbsPeak")

//    Plot1D(inW, winSize, "inW")
//    Plot1D(r_a, fftSizeH, "r_a")
//    Plot1D(r_w, fftSizeH, "r_w")
//    Plot1D(r_x.drop(fftSizeH*5), fftSizeH, "r_x")

//      val r_aN = mkAR(inW, normalize = true)
//    Plot1D((r_aN / r_w) * 1000, fftSizeH, "r_x")

//    val fft__   = Real1FFT(in = inW, size = winSize, padding = fftSize - winSize, mode = 2)
//    val pow__   = fft__.complex.absSquared
//    val ar0__   = Real1IFFT(pow__, size = fftSize, mode = 2) // / fftSize
//    val ar1__   = ResizeWindow(ar0__, fftSize, stop = -fftSizeH)
//    Plot1D(ar1__, fftSizeH, "AR1")
//    Plot1D(ar1__ / r_w, fftSizeH, "BLA")

    //    val loud        = Loudness(inW, sampleRate = sampleRate, size = winSize, spl = 70, diffuse = 1)
    //    val freq1       = freq0 * (loud > 15)
    //    val freq        = SlidingPercentile(freq1, len = 3)
    //    val hasFreq     = freq > 0

    val paths = StrongestLocalMaxima(r_x, size = fftSizeH, minLag = minLag, maxLag = maxLag,
      thresh = VoicingThreshold * 0.5, octaveCost = OctaveCost, num = NumCandidatesM)

    val lags0       = paths.lags
    val strengths0  = paths.strengths
    val lags        = BufferMemory(lags0      , fftSize * 4)
    val strengths   = BufferMemory(strengths0 , fftSize * 4)
    val peaks       = BufferMemory(peaks0     , fftSize * 2)  // WTF

//    val freqsN    = lags.reciprocal
//    val freqs     = freqsN * sampleRate
//    RepeatWindow(lags     ).poll(Metro(2), "lags")
//    RepeatWindow(freqs    ).poll(Metro(2), "freqs")
//    RepeatWindow(strengths).poll(Metro(2), "strengths")
//    strengths.poll(Metro(1000), "strengths")

    val timeStepCorr        = 0.01 * sampleRate / stepSize    // 0.87 in this case
    val octaveJumpCostC     = OctaveJumpCost      * timeStepCorr
//    val octaveJumpCostC     = OctaveJumpCost * 4
    val voicedUnvoicedCostC = VoicedUnvoicedCost  * timeStepCorr
//    val voicedUnvoicedCostC = VoicedUnvoicedCost

    val vitIn     = PitchesToViterbi(lags = lags, strengths = strengths, numIn = NumCandidatesM,
      peaks = peaks, maxLag = maxLag,
      voicingThresh = VoicingThreshold, silenceThresh = SilenceThreshold, octaveCost = OctaveCost,
      octaveJumpCost = octaveJumpCostC, voicedUnvoicedCost = voicedUnvoicedCostC)

//    Hash(vitIn).last.poll(0, "hash-vitIn")

    //    Frames(vitIn).poll(Metro(NumCandidates*NumCandidates), "vit-in")
//    Length(vitIn).poll(0, "vit-in-length")

    val states    = Viterbi(add = vitIn, numStates = NumCandidates)

//    Hash(states).last.poll(0, "hash-states")

    //    Length(states).poll(0, "path-length")
//    RepeatWindow(states).poll(Metro(2), "viterbi")

    val lagsSel   = WindowApply(BufferMemory(lags, numSteps * NumCandidates), size = NumCandidatesM, index = states,
      mode = 3)
//    val lagsSel   = WindowApply(BufferDisk(lags), size = NumCandidates, index = states)
    val hasFreq   = lagsSel > 0
    val freqsSel  = Gate(lagsSel.reciprocal, hasFreq) * sampleRate

//    Plot1D(freqsSel, size = numSteps)

//    RepeatWindow(lagsSel).poll(Metro(2), "lags-sel")
    freqsSel.poll(1, "path")
//    freqsSel.last.poll(0, "last")

//    val osc = Vector.tabulate(NumCandidates) { i =>
//      val lag       = WindowApply(lags, NumCandidates, i)
//      val hasFreq   = lag > 0
//      val freqN0    = RepeatWindow(Latch(lag.max(1).reciprocal, hasFreq), num = stepSize)
//      val freqN     = OnePole(freqN0, 0.95)
//      val strength  = WindowApply(strengths, NumCandidates, i)
//      val amp0      = RepeatWindow(strength * hasFreq, num = stepSize)
//      val amp       = OnePole(amp0, 0.95)
//      (SinOsc(freqN) + SinOsc(freqN * 2) * 0.5 + SinOsc(freqN * 3) * 0.25).take(numFrames) * amp
//    }
//
//    val mix = osc.reduce(_ + _) / NumCandidates
//    AudioFileOut(mix, fOut, AudioFileSpec(numChannels = 1, sampleRate = sampleRate,
//      sampleFormat = SampleFormat.Int24))
  }

  val config = stream.Control.Config()
  config.useAsync = false
  config.blockSize  = 512 // 4096
  implicit val ctrl: stream.Control = stream.Control(config)
  val t1 = System.currentTimeMillis()
  ctrl.run(g)

//  println("Running.")

  Await.result(ctrl.status, Duration.Inf)
  val t2 = System.currentTimeMillis()
  println(s"Took ${t2-t1} ms.")
}
