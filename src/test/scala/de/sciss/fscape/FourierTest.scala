package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.numbers
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

import scala.swing.Swing

object FourierTest extends App {
  val fIn   = userHome / "Music" / "work" / "mentasm-e8646341.aif"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"
  val fOut2 = userHome / "Music" / "work" / "_killme2.aif"
  val fOut3 = userHome / "Music" / "work" / "_killme3.aif"

  import graph._
  import numbers.Implicits._

  // stream.showStreamLog = true

  val inSpec = AudioFile.readSpec(fIn)

  lazy val gCos = Graph {
    val sr = 44100.0

    def mkIn() = {
      // DiskIn(file = fIn, numChannels = 1)
      SinOsc(freqN = 4410/sr, phase = math.Pi/2).take(10 * sr)
    }

    val in = mkIn()
    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = sr), in = in)
  }

  lazy val g = Graph {
    val sr = 44100.0

    def mkIn() = {
      val sz  = (10 * sr).toInt.nextPowerOfTwo
      val gen = SinOsc(freqN = 1.0/16, phase = math.Pi/2)
      gen.take(sz)
    }

    val in        = mkIn()
    val complex   = ZipWindow(in, DC(0.0))
    val fftSize   = inSpec.numFrames.toInt
    val fourier   = Fourier(in = complex, size = fftSize)
    val norm      = complexNormalize(fourier)
    norm.poll()
  }

  lazy val gX = Graph {
    val sr = 44100.0

    def mkIn() = {
      // DiskIn(file = fIn, numChannels = 1)
      val sz  = (10 * sr).toInt.nextPowerOfTwo
      val gen = SinOsc(freqN = 1.0/16 /* 4410/sr */, phase = math.Pi/2)
      // val gen = DC(1.0)
      gen.take(sz)
    }

    val in        = mkIn()
    val complex   = ZipWindow(in, DC(0.0))
    val fourier   = Fourier(in = complex, size = inSpec.numFrames.toInt)
    val norm      = complexNormalize(fourier)
    val unzip     = UnzipWindow(in = norm)
    val re        = ChannelProxy(unzip, 0)
    val im        = ChannelProxy(unzip, 1)
    DiskOut(file = fOut2, spec = AudioFileSpec(numChannels = 1, sampleRate = sr), in = re)
    DiskOut(file = fOut3, spec = AudioFileSpec(numChannels = 1, sampleRate = sr), in = im)
  }

  lazy val gFwdBwd = Graph {
    def mkIn() = ChannelProxy(DiskIn(file = fIn, numChannels = 1), 0)

    val in        = mkIn()
    val complex   = ZipWindow(in, DC(0.0))
    val fftSizeIn = inSpec.numFrames.toInt
    val fftSizeOut=fftSizeIn.nextPowerOfTwo
    val fwd       = Fourier(in = complex, size = fftSizeIn , dir = +1)
    val bwd       = Fourier(in = fwd    , size = fftSizeOut, dir = -1)
    val norm      = complexNormalize(bwd)
    val unzip     = UnzipWindow(in = norm)
    val re        = ChannelProxy(unzip, 0)
    // val im        = ChannelProxy(unzip, 1)
    DiskOut(file = fOut3 , spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = re)
  }

  def complexNormalize(in: GE): GE = {
    val abs       = ComplexUnaryOp(ComplexUnaryOp.Abs, in = in)
    val mag       = ChannelProxy(UnzipWindow(abs), 0)
    val max       = RunningMax(mag).last
    max.ampdb.poll(0, "max [dB]")
    val headroom  = -0.2.dbamp
    val gain      = max.reciprocal * headroom
    val buf       = BufferDisk(in)
    val sig       = buf * gain
    sig
  }

  implicit val ctrl = stream.Control()
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}