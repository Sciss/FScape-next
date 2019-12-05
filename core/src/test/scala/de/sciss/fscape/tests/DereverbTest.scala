package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control
import de.sciss.fscape.{Graph, graph}
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

import scala.swing.Swing

object DereverbTest extends App {
  lazy val g = Graph {
    import graph._
//    val fIn     = file("/home/hhrutz/Documents/devel/fdndlp/wav_sample/sample_4ch.wav")
    val fIn     = file("/home/hhrutz/Documents/devel/fdndlp/wav_sample/sample_quad-1.wav")
    val specIn  = AudioFile.readSpec(fIn)
    import specIn.{numChannels, sampleRate}
//    assert (numChannels == 4)
    val fOut        = file("/data/temp/wpe-test.aif")
    val in          = AudioFileIn(fIn, numChannels = numChannels)
    val wpe         = WPE_Dereverberate(in)
    val sig         = wpe // in - wpe
    val out         = AudioFileOut(sig, fOut, AudioFileSpec(numChannels = numChannels, sampleRate = sampleRate))
//    Progress(out / specIn.numFrames.toDouble, Metro(sampleRate))
    ProgressFrames(out, specIn.numFrames)
  }

  val config = Control.Config()
  config.useAsync = false
  var gui: SimpleGUI = _
  config.progressReporter = rep => Swing.onEDT(gui.progress = rep.total)
  implicit val ctrl: Control = Control(config)

  Swing.onEDT {
    gui = SimpleGUI(ctrl)
  }

  ctrl.run(g)
}