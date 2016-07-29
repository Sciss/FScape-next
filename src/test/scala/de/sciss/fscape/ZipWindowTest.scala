package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.numbers
import de.sciss.synth.io.{AudioFile, AudioFileSpec, AudioFileType, SampleFormat}

import scala.swing.Swing

object ZipWindowTest extends App {
  val inA = userHome / "Music" / "work" / "mentasm-1532a860.aif"
  val inB = userHome / "Music" / "work" / "mentasm-24ce9c02.aif"

  val numFramesA = AudioFile.readSpec(inA).numFrames.toInt
  val numFramesB = AudioFile.readSpec(inB).numFrames.toInt
  import numbers.Implicits._
  val fftSizeA = numFramesA.nextPowerOfTwo
  val fftSizeB = numFramesB.nextPowerOfTwo

  println(s"fftSizeA = $fftSizeA, fftSizeB = $fftSizeB")

  object Gain {
    val immediate  = Gain( 0.0.dbamp, normalized = false)
    val normalized = Gain(-0.2.dbamp, normalized = true )
  }

  object OutputSpec {
    val aiffFloat = AudioFileSpec(AudioFileType.AIFF, SampleFormat.Float, 1, 44100.0)
    // numCh, sr not used
    val aiffInt   = AudioFileSpec(AudioFileType.AIFF, SampleFormat.Int24, 1, 44100.0)
  }

  case class Gain(value: Double, normalized: Boolean = false) {
    def isUnity: Boolean = !normalized && value == 1.0
  }

  def complexNormalize(in: GE, headroom: GE): GE = {
    val mag = in.complex.abs
    normalizeImpl(in = in, mag = mag, headroom = headroom)
  }

  def realNormalize(in: GE, headroom: GE): GE =
    normalizeImpl(in = in, mag = in.abs, headroom = headroom)

  def normalizeImpl(in: GE, mag: GE, headroom: GE): GE = {
    import graph._
    val max       = RunningMax(mag).last
    val gain      = max.reciprocal * headroom
    val buf       = BufferDisk(in)
    val sig       = buf * gain
    sig
  }

  def mkFourierFwd(in: File, size: GE, gain: Gain): GE = {
    import graph._
    val disk      = AudioFileIn(file = in, numChannels = 1).take(size)
    val complex   = ZipWindow(disk, DC(0.0))
    val fft       = Fourier(in = complex, size = size, dir = +1.0)
    val sig       =
      if      (gain.isUnity   ) fft
      else if (gain.normalized) complexNormalize(fft, headroom = gain.value)
      else                      fft * gain.value
    sig
  }

  lazy val g = Graph {
    import graph._
    val fftA = mkFourierFwd(in = inA, size = fftSizeA, gain = Gain.normalized)
    // val fftB = mkFourierFwd(in = inB, size = fftSizeB, gain = Gain.immediate)

    val fftAZ0 = UnzipWindow(fftA) // treat Re and Im as two channels
    // val fftBZ = UnzipWindow(fftB) // treat Re and Im as two channels
    val fftAZ = fftAZ0.elastic()
//    val fftAZ = BufferDisk(fftAZ0)

    val numFrames = math.min(fftSizeA, fftSizeB)
    assert(numFrames.isPowerOfTwo)
    fftAZ.poll(1.0/44100, "A")
    // fftBZ.poll(1.0/44100, "B")
  }

  // showStreamLog = true

  val config = stream.Control.Config()
  config.useAsync = false // for debugging
  val ctrl = stream.Control(config)
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}
