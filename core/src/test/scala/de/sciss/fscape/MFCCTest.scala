package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

import scala.swing.Swing

object MFCCTest extends App {
  val dir     = userHome / "Music" / "work"
  val fIn     = dir / "TubewayArmy-DisconnectFromYouEdit-L.aif"
  val fOut    = dir / "_killme.aif"
  val specIn  = AudioFile.readSpec(fIn)

  val config = stream.Control.Config()
  var gui: SimpleGUI = _
  config.progressReporter = rep => Swing.onEDT(gui.progress = rep.total)
  config.useAsync = false

  lazy val g0 = Graph {
    import graph._
    import specIn.{sampleRate, numChannels}
    val in        = AudioFileIn(fIn, numChannels = numChannels)

    val fftSize   = 1024
    val stepSize  = fftSize / 2
    val numMel    = 42
    val numCoef   = 13

    val lap       = Sliding (in , fftSize, stepSize) * GenWindow(fftSize, GenWindow.Hann)
    val fft       = Real1FFT(lap, fftSize, mode = 1)
    val mag       = fft.complex.mag
    val mel       = MelFilter(mag, fftSize/2, bands = numMel,
      minFreq = 55, maxFreq = sampleRate/2, sampleRate = sampleRate)
    val mfcc      = DCT_II(mel.log, numMel, numCoef, zero = 0)

    // reconstruction of what strugatzki's 'segmentation' is doing (first step)
    val covSize   = numCoef * 32
    val mfccSlid  = Sliding(mfcc, numCoef, 1)
    val mfccSlidT = mfccSlid.drop(covSize)
    val el: Int   = (covSize / config.blockSize) + 1
    val cov       = Pearson(mfccSlid.elastic(n = el), mfccSlidT, covSize)

    val sig       = cov
    AudioFileOut(fOut, AudioFileSpec(numChannels = numChannels, sampleRate = sampleRate), in = sig)
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
    val fft       = Real1FFT(lap, fftSize, mode = 1)
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