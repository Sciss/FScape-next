package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

import scala.swing.Swing

object MFCCTest extends App {
  val dir     = userHome / "Music" / "work"
  val fOut    = dir / "_killme.aif"
  val fOut2   = dir / "_killme2.aif"
  val fOut3   = dir / "_killme3.aif"

  val config = stream.Control.Config()
  var gui: SimpleGUI = _
  config.progressReporter = rep => Swing.onEDT(gui.progress = rep.total)
  config.useAsync = false

  case class Settings(fIn: File, fftSize: Int = 2048, stepDiv: Int = 4, numMel: Int = 42, numCoef: Int = 21,
                      sideDur: Double = 0.5, spaceDur: Double = 0.5, fadeDur: Double = 0.5, numTop0: Int = 0)

  val setUniv = Settings(userHome/"Documents"/"projects"/"Imperfect"/"audio_work"/"B19h43m37s22aug2016.wav")
  val setTube = Settings(dir / "TubewayArmy-DisconnectFromYouEdit-L.aif", spaceDur = 3)
  val setAne  = Settings(userHome/"Documents"/"projects"/"Anemone"/"rec"/"BURNED"/"Anemone_xCoAx_160707_21h27.aif",
    spaceDur = 0.3, fadeDur = 0.3, stepDiv = 8)
  val set     = setAne

  lazy val g = Graph {
    import graph._
    import set._

    val specIn  = AudioFile.readSpec(fIn)
    import specIn.{sampleRate, numChannels, numFrames}
    def mkIn()      = AudioFileIn(fIn, numChannels = numChannels)
    val in          = mkIn()
    val stepSize    = fftSize / stepDiv
    val sideFrames  = (sampleRate * sideDur ).toInt
    val spaceFrames = (sampleRate * spaceDur).toInt
    val spaceLen    = spaceFrames / stepSize
    val fadeFrames  = (sampleRate * fadeDur).toInt
    val sideLen     = math.max(1, sideFrames / stepSize) // 24
    val numTop      = if (numTop0 > 0) numTop0 else (numFrames / (spaceFrames * 3)).toInt
    val covSize     = numCoef * sideLen
    val numCov      = numFrames / stepSize - (2 * sideLen)

    val inMono      = if (numChannels == 1) in else ChannelProxy(in, 0) + ChannelProxy(in, 1) // XXX TODO --- missing Mix
    val lap         = Sliding (inMono, fftSize, stepSize) * GenWindow(fftSize, GenWindow.Hann)
    val fft         = Real1FFT(lap, fftSize, mode = 2)
    val mag         = fft.complex.mag
    val mel         = MelFilter(mag, fftSize/2, bands = numMel,
      minFreq = 60, maxFreq = 14000, sampleRate = sampleRate)
    val mfcc        = DCT_II(mel.log, numMel, numCoef, zero = 0)

    // reconstruction of what strugatzki's 'segmentation' is doing (first step)
    val mfccSlid    = Sliding(mfcc, covSize, numCoef)
    val mfccSlidT   = mfccSlid.drop(covSize)
    val el: Int     = (covSize / config.blockSize) + 1
    val cov0        = Pearson(mfccSlid.elastic(n = el), mfccSlidT, covSize)
    val cov         = cov0.take(numCov)

    val covNeg      = -cov + 1  // N.B. not `1 - cov` because binary-op-ugen stops when first input stops
    val covMin0     = DetectLocalMax(covNeg, size = spaceLen)
    val covMin      = covMin0.take(numCov)  // XXX TODO --- bug in DetectLocalMax?

    val keys        = covNeg.elastic() * covMin
    val values      = Frames(keys) - 1

    Progress(values * stepSize / (2 * numFrames), Metro(sampleRate/stepSize), label = "analysis")

    val top10       = PriorityQueue(keys  , values, size = numTop)  // lowest covariances mapped to frames
    val framesDesc0 = PriorityQueue( top10, top10 , size = numTop)  // frames in descending order
    val framesAsc0  = PriorityQueue(-top10, top10 , size = numTop)  // frames in ascending order
    // if we do _not_ add `sideLen`, we ensure the breaking change comes after the calculated frame
    val framesDescF = framesDesc0 /* + sideLen */ * stepSize
    val framesAscF  = framesAsc0  /* + sideLen */ * stepSize
    val spansDesc   = (framesDescF :+ 0L) zip (numFrames +: framesDescF)

    val inDup       = mkIn()
    val slices      = Slices(inDup, spansDesc)
    val spanLenAsc  = (framesAscF :+ numFrames) - (0L +: framesAscF)

    val reconWindow = GenWindow(spanLenAsc, GenWindow.Hann).pow(1.0/8)
    val slicesWin   = slices * reconWindow
    val slicesLap   = OverlapAdd(slicesWin, size = spanLenAsc, step = spanLenAsc - fadeFrames)

    val sig         = slicesLap
    val out         = AudioFileOut(fOut, AudioFileSpec(numChannels = numChannels, sampleRate = sampleRate), in = sig)
    Progress(out / (2 * numFrames), Metro(sampleRate), label = "write")
  }

  val ctrl  = stream.Control(config)

  Swing.onEDT {
    gui = SimpleGUI(ctrl)
  }

  ctrl.run(g)
}