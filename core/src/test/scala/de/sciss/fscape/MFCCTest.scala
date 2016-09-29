package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

import scala.swing.Swing

object MFCCTest extends App {
  val dir     = userHome / "Music" / "work"
//  val fIn     = dir / "TubewayArmy-DisconnectFromYouEdit-L.aif"
//  val fIn     = userHome/"Documents"/"applications/150501_DEGEM_CD/pcm/Derrida-Jacques_Circumfession_Disc-1_01_Track-1.aif"
  val fIn     = userHome/"Documents"/"projects"/"Imperfect"/"audio_work"/"B19h43m37s22aug2016.wav"
  val fOut    = dir / "_killme.aif"
  val fOut2   = dir / "_killme2.aif"
  val fOut3   = dir / "_killme3.aif"
  val specIn  = AudioFile.readSpec(fIn)

  val config = stream.Control.Config()
  var gui: SimpleGUI = _
  config.progressReporter = rep => Swing.onEDT(gui.progress = rep.total)
  config.useAsync = false

  lazy val g0 = Graph {
    import graph._

    import specIn.{sampleRate, numChannels, numFrames}
    def mkIn()      = AudioFileIn(fIn, numChannels = numChannels)
    val in          = mkIn()
//    val numChannels = 1
//    val numFrames   = 44100 * 8
//    val sampleRate  = 44100.0
//    val in          = (SinOsc(441/sampleRate).take(44100 * 3) ++ SinOsc(882/sampleRate).take(44100 * 2) ++ SinOsc(441/sampleRate).take(44100 * 3)) * 0.5

    val fftSize     = 2048 // 1024
    val stepSize    = fftSize / 4  // 2
    val numMel      = 42
    val numCoef     = 21 // 13
    val sideFrames  = (sampleRate * 0.5).toInt // 4410 // 22050
    val spaceFrames = sampleRate.toInt * 0.5 // * 2 // 4
    val spaceLen    = spaceFrames / stepSize
    val sideLen     = math.max(1, sideFrames / stepSize) // 24
    val numTop      = (numFrames / (spaceFrames * 3)).toInt
    val covSize     = numCoef * sideLen
    val numCov      = numFrames / stepSize - (2 * sideLen)

    val inMono    = if (numChannels == 1) in else ChannelProxy(in, 0) + ChannelProxy(in, 1) // XXX TODO --- missing Mix
    val lap       = Sliding (inMono, fftSize, stepSize) * GenWindow(fftSize, GenWindow.Hann)
    val fft       = Real1FFT(lap, fftSize, mode = 2)
    val mag       = fft.complex.mag
    val mel       = MelFilter(mag, fftSize/2, bands = numMel,
      minFreq = 60, maxFreq = 14000, sampleRate = sampleRate)
    val mfcc      = DCT_II(mel.log, numMel, numCoef, zero = 0)

    // reconstruction of what strugatzki's 'segmentation' is doing (first step)
//    val mfccSlid  = Sliding(mfcc, numCoef, 1)
    val mfccSlid  = Sliding(mfcc, covSize, numCoef)
    val mfccSlidT = mfccSlid.drop(covSize)
    val el: Int   = (covSize / config.blockSize) + 1
    val cov0      = Pearson(mfccSlid.elastic(n = el), mfccSlidT, covSize)
    val cov       = cov0.take(numCov)

    val covNeg    = -cov + 1  // N.B. not `1 - cov` because binary-op-ugen stops when first input stops
    val covMin0   = DetectLocalMax(covNeg, size = spaceLen /* XXX TODO -- which size? */)
    val covMin    = covMin0.take(numCov)  // XXX TODO --- bug in DetectLocalMax

//    cov.elastic().poll(covMin, " 0")
//    cov.elastic().poll(covMin.drop(1), "-1")
//    cov.elastic().drop(1).poll(covMin, "+1")

    val keys      = covNeg.elastic() * covMin
    val values    = Frames(keys)

    Progress(values * stepSize / (2 * numFrames), Metro(sampleRate/stepSize), label = "analysis")

    val top10     = PriorityQueue(keys, values, size = numTop)  // lowest covariances mapped to frames
//    val top10S    = PriorityQueue(-top10, top10, size = 10)  // frames in ascending order
//    ResizeWindow(top10S, 1, 0, 1).poll(Metro(2), "frame") // XXX TODO -- we need a shortcut for this

    val top10Desc = PriorityQueue(top10, top10, size = numTop)  // frames in descending order
    // if we do _not_ add `sideLen`, we ensure the breaking change comes after the calculated frame
    val frames      = numFrames +: ((top10Desc /* + sideLen */) * stepSize) :+ 0L
    val spanStarts  = frames.tail
    val spanStops   = frames
    val spans       = spanStarts zip spanStops
//    ResizeWindow(spans, 1, 0, 1).poll(Metro(2), "frame") // XXX TODO -- we need a shortcut for this

    val inDup     = mkIn()
    val slices    = Slices(inDup, spans)
//    val spanLengths = spanStops - spanStarts
//    val sliceWins = ResizeWindow(spanLengths, size = 1, start = 0, XXX TODO)

    val sig       = slices
    val out       = AudioFileOut(fOut, AudioFileSpec(numChannels = numChannels, sampleRate = sampleRate), in = sig)
    Progress(out / (2 * numFrames), Metro(sampleRate), label = "write")

//    val covMin1   = RunningMin(cov).last
//    val covMax1   = RunningMax(cov).last
//    val sig1      = (BufferDisk(cov) - covMin1) / (covMax1 - covMin1) * -1 + 1 // keys
//    AudioFileOut(fOut2, AudioFileSpec(numChannels = numChannels, sampleRate = sampleRate), in = sig1)
//    AudioFileOut(fOut3, AudioFileSpec(numChannels = numChannels, sampleRate = sampleRate), in = covMin)
  }

  lazy val g1 = Graph {
    import graph._
    import specIn.{sampleRate, numChannels, numFrames}
    val in        = AudioFileIn(fIn, numChannels = numChannels)

    val fftSize   = 1024
      val stepSize  = fftSize / 2
    val numMel    = fftSize/2
    val numCoef   = numMel - 1

    val lap       = Sliding(in, fftSize, stepSize) * GenWindow(fftSize, GenWindow.Hann)
    val fft       = Real1FFT(lap, fftSize, mode = 2)
    val mag       = fft.complex.mag
    val mel       = MelFilter(mag, fftSize/2, bands = numMel,
      minFreq = 55, maxFreq = sampleRate/2, sampleRate = sampleRate)
    val mfcc      = DCT_II(mel.log.max(-80), numMel, numCoef + 1, zero = 0)
    val rec       = mfcc * GenWindow(numCoef, GenWindow.Hann)
//    rec.poll(Metro(44100), "rec")
    val sig0      = OverlapAdd(rec, size = numCoef, step = numCoef/2)
//    sig0.poll(Metro(44100), "olap")

    def normalize(in: GE, headroom: GE = 1): GE = {
      val max       = RunningMax(in.abs).last
      val gain      = max.reciprocal * headroom
      val buf       = BufferDisk(in)
      buf * gain
    }

    val f1    = Timer(DC(0)).matchLen(sig0)
    val prog1 = f1/numFrames.toDouble
//    f1.poll(Metro(44100), "prog1")
    Progress(prog1, Metro(44100))
    val sig = normalize(sig0)
    val f2  = AudioFileOut(fOut, AudioFileSpec(numChannels = numChannels, sampleRate = sampleRate), in = sig)
    Progress(f2/numFrames.toDouble, Metro(44100))
    // frames.last.poll(0, "num-frames")
  }

  val ctrl  = stream.Control(config)

  Swing.onEDT {
    gui = SimpleGUI(ctrl)
  }

  ctrl.run(g0)
}