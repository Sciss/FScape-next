package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

import scala.swing.Swing

object SlicesTest extends App {
  val dir     = userHome / "Music" / "work"
  val fIn     = dir / "TubewayArmy-DisconnectFromYouEdit-L.aif"
  val fOut    = dir / "_killme.aif"
  val fOut2   = dir / "_killme2.aif"
  val specIn  = AudioFile.readSpec(fIn)

  val config = stream.Control.Config()
  var gui: SimpleGUI = _
  config.progressReporter = rep => Swing.onEDT(gui.progress = rep.total)
  config.useAsync = false

  lazy val g0 = Graph {
    import graph._
    import specIn.{sampleRate, numChannels, numFrames}
    val in        = AudioFileIn(fIn, numChannels = numChannels)
//    val numChannels = 1
//    val numFrames   = 10000 // 8192
//    val sampleRate  = 44100.0
//    val in          = SinOsc(441/sampleRate).take(numFrames)

    val frames    = Timer(DC(0)).take(numFrames) //  Frames(in) -- need some elastic somewhere
    val stop      = -frames + numFrames // ! constant arg must be b-operand
    val start     = stop - 1
    val spans     = start zip stop
    val reverse   = Slices(in, spans)

    val sig       = reverse
    val out       = AudioFileOut(fOut , AudioFileSpec(numChannels = numChannels, sampleRate = sampleRate), in = sig)
    /* val out2 = */AudioFileOut(fOut2, AudioFileSpec(numChannels = numChannels, sampleRate = sampleRate), in = in )
    Progress(out / numFrames.toDouble, Metro(44100))
  }

  val ctrl  = stream.Control(config)

  Swing.onEDT {
    gui = SimpleGUI(ctrl)
  }

  ctrl.run(g0)
}