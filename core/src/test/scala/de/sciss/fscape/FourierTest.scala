package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control
import de.sciss.numbers
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

import scala.swing.Swing

object FourierTest extends App {
//  val fIn   = userHome / "Music" / "work" / "mentasm-e8646341.aif"
  val fIn   = userHome / "Documents" / "projects" / "Anemone"/ "minuten" / "rec" / "AnemoneRehearsal160527_15h24m.aif"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"
  val fOut2 = userHome / "Music" / "work" / "_killme2.aif"
  val fOut3 = userHome / "Music" / "work" / "_killme3.aif"

  import graph._
  import numbers.Implicits._

  // showGraphLog = true

  val inSpec = AudioFile.readSpec(fIn)

  lazy val gCos = Graph {
    val sr = 44100.0

    def mkIn() = {
      // DiskIn(file = fIn, numChannels = 1)
      SinOsc(freqN = 4410/sr, phase = math.Pi/2).take(10 * sr)
    }

    val in = mkIn()
    AudioFileOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = sr), in = in)
  }

  lazy val gDebug = Graph {
    val sr = 44100.0

    val sz        = (10 * sr).toInt.nextPowerOfTwo
    val gen       = SinOsc(freqN = 1.0/16, phase = math.Pi/2)
    val in        = gen.take(sz)
    val complex   = ZipWindow(in, DC(0.0))
    val fftSize   = inSpec.numFrames.toInt
    val fourier   = Fourier(in = complex, size = fftSize)

    val mag       = ChannelProxy(UnzipWindow(fourier), 0)
    val max       = RunningMax(mag).last
    max.ampdb.poll(0, "max [dB]")
    val gain      = max.reciprocal

    gain.poll
  }

  lazy val g = Graph {
    val sr = 44100.0
    val sz = (10 * sr).toInt.nextPowerOfTwo

    def mkIn() = {
      // DiskIn(file = fIn, numChannels = 1)
      val gen = SinOsc(freqN = 1.0/16 /* 4410/sr */, phase = 0 /* math.Pi/2 */)
      // val gen = DC(1.0)
      gen.take(sz)
    }

    val in        = mkIn()
    val complex   = ZipWindow(in, DC(0.0))
    val fftSize   = sz // inSpec.numFrames.toInt
    val fourier   = Fourier(in = complex, size = fftSize)
    val norm      = complexNormalize(fourier)
//    val unzip     = UnzipWindow(in = norm)
//    val re        = ChannelProxy(unzip, 0)
//    val im        = ChannelProxy(unzip, 1)
//    val p1        = AudioFileOut(file = fOut2, spec = AudioFileSpec(numChannels = 1, sampleRate = sr), in = re)
//    val p2        = AudioFileOut(file = fOut3, spec = AudioFileSpec(numChannels = 1, sampleRate = sr), in = im)
//    val progress  = p1 / sz // (p1 + p2) / (sz * 2)
//    Progress(progress, Metro(sr))
  }

  lazy val gFwdBwd = Graph {
    val sr = 44100.0

    val sz  = (10 * sr).toInt.nextPowerOfTwo
    def mkIn() = {
      // DiskIn(file = fIn, numChannels = 1)
      val gen = SinOsc(freqN = 1.0/16 /* 4410/sr */, phase = math.Pi/2)
      // val gen = DC(1.0)
      gen.take(sz)
    }

    val in        = mkIn()
    val complex   = ZipWindow(in, DC(0.0))
    val fftSizeIn = sz // sinSpec.numFrames.toInt
    assert(fftSizeIn.isPowerOfTwo)
    val fftSizeOut = fftSizeIn.nextPowerOfTwo
    assert(fftSizeOut == fftSizeIn)
    val fwd       = Fourier(in = complex, size = fftSizeIn , dir = +1)
    val bwd       = Fourier(in = fwd    , size = fftSizeOut, dir = -1)
    val norm      = complexNormalize(bwd)
    val unzip     = UnzipWindow(in = norm)
    val re        = ChannelProxy(unzip, 0)
    // val im        = ChannelProxy(unzip, 1)
    AudioFileOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = re)
  }

  def complexNormalize(in: GE): GE = {
    val abs       = in.complex.abs // ComplexUnaryOp(ComplexUnaryOp.Abs.id, in = in)
    val mag       = ChannelProxy(UnzipWindow(abs), 0)
    val max       = RunningMax(mag).last
    max.ampdb.poll(0, "max [dB]")
    val headroom  = -0.2.dbamp
    val gain      = max.reciprocal * headroom
    val buf       = BufferDisk(in)
//    buf.poll(Metro(44100), "buf")
    val sig       = buf * gain
//    val sig       = buf hypotx DC(gain)
    sig.poll(Metro(44100), "sig")
    sig
  }

  val config = Control.Config()
  var gui: SimpleGUI = _
  config.progressReporter = rep => Swing.onEDT(gui.progress = rep.total)
  implicit val ctrl = Control(config)

  Swing.onEDT {
    gui = SimpleGUI(ctrl)
  }

  ctrl.run(g)

  println("Running.")
}