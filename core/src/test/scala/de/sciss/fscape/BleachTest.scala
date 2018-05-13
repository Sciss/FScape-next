package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control
import de.sciss.numbers.Implicits._
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

import scala.swing.Swing

object BleachTest extends App {
  lazy val g = Graph {
    import graph._
    val fIn     = userHome / "Music" / "TubewayArmy-DisconnectFromYouEdit.aif"
    val specIn  = AudioFile.readSpec(fIn)
    import specIn.{numChannels, sampleRate}
//    val numChannels = 1
    val fOut      = userHome / "Documents" / "temp" / "test.aif"
    val fltLen    = 441
    val feedback  = -50.0.dbAmp // -60.0.dbamp
    val clip      =  18.0.dbAmp
    val twoWays   = false
    val inverse   = false

    val in        = AudioFileIn(fIn, numChannels = specIn.numChannels)
//    val in        = ChannelProxy(in0, 0)
    val sig0      = Bleach(in = in, filterLen = fltLen, feedback = feedback, filterClip = clip)
    val sig       = if (inverse) sig0 else in.elastic() - sig0
    require(!twoWays, "twoWays - not yet implemented")

    val out     = AudioFileOut(fOut, AudioFileSpec(numChannels = numChannels, sampleRate = sampleRate), in = sig)
    Progress(out / specIn.numFrames.toDouble, Metro(44100))
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