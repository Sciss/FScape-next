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

  import graph._
  import numbers.Implicits._

  // stream.showStreamLog = true

  val inSpec = AudioFile.readSpec(fIn)

  lazy val g = Graph {
    def mkIn() = DiskIn(file = fIn, numChannels = 1)

    val in        = mkIn()
    val complex   = ZipWindow(in, DC(0.0))
    val fourier   = Fourier(in = complex, size = inSpec.numFrames.toInt)
    val norm      = complexNormalize(fourier)
    val unzip     = UnzipWindow(in = norm)
    val re        = ChannelProxy(unzip, 0)
    val im        = ChannelProxy(unzip, 1)
    DiskOut(file = fOut , spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = re)
    DiskOut(file = fOut2, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = im)
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