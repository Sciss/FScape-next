package de.sciss.fscape

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.numbers
import de.sciss.synth.io.AudioFileSpec

import scala.concurrent.ExecutionContext
import scala.swing.Swing

object NormalizeTest extends App {
  val fIn   = userHome / "Documents" / "projects" / "Unlike" / "audio_work" / "mentasm-e8646341-63dcf8a8.aif"
  val fIn2  = userHome / "Music" / "work" / "B20h22m33s19mar2016.wav"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"

  import graph._
  import numbers.Implicits._

  // stream.showStreamLog = true

  lazy val gOLD = Graph {
    val trig  = Impulse(1.0/44100)
    val in    = DiskIn(file = fIn, numChannels = 1)
    Poll(in = in, trig = trig, label = "test")
  }

  lazy val gFORK2 = Graph {
    def mkIn() = DiskIn(file = fIn, numChannels = 1)

    val in        = mkIn()
    val max       = RunningMax(in.abs) // .last
    val trig  = Impulse(1.0/44100)
    in /* max */     . poll(trig, "max [Lin]")
    in /* max */.ampdb.poll(trig, "max [dB ]")
  }

  lazy val gFORK = Graph {
    val in    = DC(-33.0)
    val trig  = Impulse(1.0/44100)
    in     .poll(trig, "[Lin]")
    in .abs.poll(trig, "[Abs]")
  }

  lazy val gConst = Graph {
    val trig = 1 // Impulse(1.0/44100)
    Poll(in = (-0.0940551906824112: GE).abs.ampdb, trig = trig, label = "max")
  }

  lazy val g = Graph {
    def mkIn() = ChannelProxy(DiskIn(file = fIn2, numChannels = 2), 0)

    val in        = mkIn()
    val max       = RunningMax(in.abs) // .last
    max.ampdb.poll(1.0/44100, "max [dB]")
    val headroom  = -0.2.dbamp
    val gain      = max.reciprocal * headroom
    val buf       = mkIn() // BufferAll(in)
    val sig       = buf * gain
    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig)
  }

  lazy val gX = Graph {
    def mkIn() = DiskIn(file = fIn, numChannels = 1)

    val in        = mkIn()
    val max       = RunningMax(in.abs).last
    max.ampdb.poll(0, "max [dB]")
    val headroom  = -0.2.dbamp
    val gain      = max.reciprocal * headroom
    val buf       = mkIn() // BufferAll(in)
    val sig       = buf * gain
    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig)
  }

  // XXX TODO -- reduce ceremony here
  import ExecutionContext.Implicits.global
  implicit val ctrl   = stream.Control(bufSize = 1024)
  val process         = g.expand
  implicit val system = ActorSystem()
  implicit val mat    = ActorMaterializer()
  process.runnable.run()

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}