package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.{GE, Graph, stream}
import de.sciss.numbers.Implicits._
import de.sciss.synth.io.AudioFile

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object TestACIntensity extends App {
  val fIn     = file("/data/projects/Maeanderungen/audio_work/edited/HB_0_HH_T168.wav")
  val specIn  = AudioFile.readSpec(fIn)

  def any2stringadd: Any = ()

  lazy val g = Graph {
    import de.sciss.fscape.graph._
    val in            = AudioFileIn(file = fIn, numChannels = 1)
    val winSize       = 1500 // 2000
    val winPadded     = (winSize * 1.5).ceil.toInt
    val fftSize       = winPadded.nextPowerOfTwo
    val fftSizeH: Int = fftSize/2
    val stepSize      = winSize / 4
    val inSlid        = Sliding (in = in , size = winSize, step = stepSize)

    def mkWindow() = GenWindow(winSize, shape = GenWindow.Hann)

    val inW = {
      val leak = NormalizeWindow(inSlid, winSize, mode = NormalizeWindow.ZeroMean)
      leak * mkWindow()
    }

    def mkAR(sig: GE, normalize: Boolean = true) = {
      val fft   = Real1FFT(in = sig, size = winSize, padding = fftSize - winSize, mode = 2)
      val pow   = fft.complex.absSquared
      val ar0   = Real1IFFT(pow, size = fftSize, mode = 2) // / fftSize
      val ar1   = ResizeWindow(ar0, fftSize, stop = -fftSizeH)
      if (!normalize) ar1 else NormalizeWindow(ar1, size = fftSizeH, mode = NormalizeWindow.Normalize)
    }

    val r_a = mkAR(inW, normalize = false)
    val r_w = mkAR(mkWindow())
    val r_x = r_a / r_w

    val localMax  = WindowApply(RunningMax(in.abs, Metro(winSize)), winSize, winSize - 1) // .take(7000)
    Frames(localMax).poll(localMax sig_== 0, "ZERO")
    val rx0       = WindowApply(r_x, fftSizeH, index = 0) // .take(7000)
//    val rx1       = rx0.exp / 1300 // rx0.sqrt * 4.5
    val rx1       = rx0.sqrt // * 4
    val ratio     = BufferDisk(rx1) / BufferDisk(localMax)
    val num       = Length(ratio)
    num.poll(0, "len")
    val sum       = RunningSum(ratio).last
    val min       = RunningMin(ratio).last
    val max       = RunningMax(ratio).last
    val mean      = sum.elastic() / num.elastic()
    val min0      = min.elastic()
    val max0      = max.elastic()
    mean.poll(0, "mean")
    min0.poll(0, "min ")
    max0.poll(0, "max ")

//    rx1.poll(Metro(4), "foo")

//    RepeatWindow(SlidingPercentile(ratio, len = 999, frac = 0.1)).poll(Metro(10), "10% percentile")
    val medL    = 101 // 999
    val p10     = SlidingPercentile(ratio, len = medL, frac = 0.1)
    val p90     = SlidingPercentile(ratio, len = medL, frac = 0.9)
    val median  = SlidingPercentile(ratio, len = medL, frac = 0.5)
//    RepeatWindow(median).poll(Metro(10), "50% percentile")
//    RepeatWindow(SlidingPercentile(ratio, len = 999, frac = 0.9)).poll(Metro(10), "90% percentile")

    val medianSum = RunningSum(median).last
    val medianNum = Length(median)
    val medianMean = medianSum.elastic() / medianNum.elastic()
    medianMean.poll(0, "median-mean")

    val p10Sum = RunningSum(p10).last
    val p10Num = Length(p10)
    val p10Mean = p10Sum.elastic() / p10Num.elastic()
    p10Mean.poll(0, "p10-mean")

    val p90Sum = RunningSum(p90).last
    val p90Num = Length(p90)
    val p90Mean = p90Sum.elastic() / p90Num.elastic()
    p90Mean.poll(0, "p90-mean")

    //    RepeatWindow(rx0).poll(Metro(2), "ac[0]")
  }

  val config = stream.Control.Config()
  config.useAsync = false
  implicit val ctrl: stream.Control = stream.Control(config)
  ctrl.run(g)

  Await.result(ctrl.status, Duration.Inf)
}
