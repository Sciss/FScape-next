package de.sciss.fscape.tests

import de.sciss.audiofile.AudioFileSpec
import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.{GE, Graph, graph, stream}
import de.sciss.numbers

import scala.swing.Swing

object NormalizeTest extends App {
  val fIn   = userHome / "Documents" / "projects" / "Unlike" / "audio_work" / "mentasm-e8646341-63dcf8a8.aif"
  val fIn2  = userHome / "Music" / "work" / "B20h22m33s19mar2016.wav"
  val fIn3  = userHome / "Music" / "work" / "_killme1.wav"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"

  import graph._
  import numbers.Implicits._

  // stream.showStreamLog = true

  lazy val gOLD = Graph {
    val trig  = Metro(44100)
    val in    = AudioFileIn(file = fIn.toURI, numChannels = 1)
    Poll(in = in, gate = trig, label = "test")
  }

  lazy val gFORK2 = Graph {
    def mkIn() = AudioFileIn(file = fIn.toURI, numChannels = 1)

    val in        = mkIn()
    /* val max = */ RunningMax(in.abs) // .last
    val trig  = Metro(44100)
    in /* max */     . poll(trig, "max [Lin]")
    in /* max */.ampDb.poll(trig, "max [dB ]")
  }

  lazy val gFORK = Graph {
    val in    = DC(-33.0)
    val trig  = Metro(44100)
    in     .poll(trig, "[Lin]")
    in .abs.poll(trig, "[Abs]")
  }

  lazy val gConst = Graph {
    val trig = 1 // Impulse(1.0/44100)
    Poll(in = (-0.0940551906824112: GE).abs.ampDb, gate = trig, label = "max")
  }

  lazy val g = Graph {
    //    def mkIn() = ChannelProxy(DiskIn(file = fIn2, numChannels = 2), 0)
    //    def mkIn() = DiskIn(file = fIn, numChannels = 1)
    def mkIn() = AudioFileIn(file = fIn3.toURI, numChannels = 1)

    val in        = mkIn()
    val max       = RunningMax(in.abs).last
    max.ampDb.poll(0, "max [dB]")
    val headroom  = -0.2.dbAmp
    val gain      = max.reciprocal * headroom
    val buf       = mkIn() // BufferAll(in)
    val sig       = buf * gain
    AudioFileOut(file = fOut.toURI, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig)
  }

  lazy val gBuf = Graph {
    val in        = AudioFileIn(file = fIn3.toURI, numChannels = 1)
    val max       = RunningMax(in.abs).last
    max.ampDb.poll(0, "max [dB]")
    val headroom  = -0.2.dbAmp
    val gain      = max.reciprocal * headroom
    val buf       = BufferDisk(in)
    val sig       = buf * gain
    AudioFileOut(file = fOut.toURI, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig)
  }

  lazy val gX = Graph {
    def mkIn() = AudioFileIn(file = fIn.toURI, numChannels = 1)

    val in        = mkIn()
    val max       = RunningMax(in.abs).last
    max.ampDb.poll(0, "max [dB]")
    val headroom  = -0.2.dbAmp
    val gain      = max.reciprocal * headroom
    val buf       = mkIn() // BufferAll(in)
    val sig       = buf * gain
    AudioFileOut(file = fOut.toURI, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig)
  }

  implicit val ctrl: stream.Control = stream.Control()
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}