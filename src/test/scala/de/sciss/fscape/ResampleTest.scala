package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.synth.io.AudioFileSpec

import scala.swing.Swing

object ResampleTest extends App {
  lazy val g1 = Graph {
    import graph._
    val sr    = 44100.0
    val in0   = SinOsc(441/sr)
//    val in0   = WhiteNoise()
    val in    = in0.take(sr.toLong * 1.0)
    val factor = 0.5
//    val factor = 1.0
//        val factor = 2.0
    val sig   = Resample(in = in, factor = factor)
    val factorI = (factor * 100).toInt
    val fOut  = userHome / "Documents" / "temp" / s"resample_$factorI.aif"
    AudioFileOut(file = fOut, spec = AudioFileSpec(sampleRate = sr, numChannels = 1), in = sig)
  }

  lazy val g2 = Graph {
    import graph._
    val sr    = 44100.0
    val in0   = SinOsc(441/sr)
    //    val in0   = WhiteNoise()
    val in    = in0.take(sr.toLong * 10)
    val factor = SinOsc(1.0/32).linexp(-1.0, 1.0, 0.5, 2.0)
    val sig   = Resample(in = in, factor = factor, minFactor = 0.5)
    val fOut  = userHome / "Documents" / "temp" / "resample_mod.aif"
    AudioFileOut(file = fOut, spec = AudioFileSpec(sampleRate = sr, numChannels = 1), in = sig)
  }

  lazy val g = Graph {
    import graph._
    val sr      = 44100.0
    val in0     = SinOsc(220.5/sr)
    val len     = sr.toLong * 10
    val in      = in0 // .take(len)
    val factor  = Line(1.0, 0.1, len)
    val sig0    = Resample(in = in, factor = factor, minFactor = 0.1)
    val sig     = sig0.take(len)
    val fOut    = userHome / "Documents" / "temp" / "resample_mod.aif"
    AudioFileOut(file = fOut, spec = AudioFileSpec(sampleRate = sr, numChannels = 1), in = sig)
  }

  val config = stream.Control.Config()
  config.useAsync   = false
  config.blockSize  = 100 // test
  implicit val ctrl = stream.Control(config)
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }
}