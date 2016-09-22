package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.numbers.Implicits._
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

import scala.swing.Swing

object BleachTest extends App {
  lazy val g = Graph {
    import graph._
    val fIn     = userHome / "Music" / "TubewayArmy-DisconnectFromYouEdit.aif"
    val specIn  = AudioFile.readSpec(fIn)
//    import specIn.{numChannels, sampleRate}
    import specIn.sampleRate
    val numChannels = 1
    val fOut    = userHome / "Documents" / "temp" / "test.aif"
    val fltLen  = 8 // 441
    val feedback= -50.0.dbamp // -60.0.dbamp
    val clip    =  18.0.dbamp
    val twoWays = false
    val inverse = true // false

    val in0     = AudioFileIn(fIn, numChannels = specIn.numChannels)
    val in      = ChannelProxy(in0, 0)
    val sig0    = Bleach(in = in, filterLen = fltLen, feedback = feedback, filterClip = clip)
    val sig     = if (inverse) sig0 else in - sig0
    require(!twoWays, "twoWays - not yet implemented")

    val out     = AudioFileOut(fOut, AudioFileSpec(numChannels = numChannels, sampleRate = sampleRate), in = sig)
    Progress(out / specIn.numFrames.toDouble, Metro(44100))
  }

  val config = stream.Control.Config()
  config.useAsync = false
  var gui: SimpleGUI = _
  config.progressReporter = rep => Swing.onEDT(gui.progress = rep.total)
  implicit val ctrl = stream.Control(config)
  ctrl.run(g)

  Swing.onEDT {
    gui = SimpleGUI(ctrl)
  }
}