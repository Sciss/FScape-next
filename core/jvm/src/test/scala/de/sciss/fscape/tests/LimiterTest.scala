package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control
import de.sciss.fscape.{Graph, graph}
import de.sciss.numbers.Implicits._
import de.sciss.audiofile.{AudioFile, AudioFileSpec}

import scala.swing.Swing

object LimiterTest extends App {
  lazy val g = Graph {
    import graph._
    val fIn     = file("/data/temp/rec180912_142157-min-phase.aif")
    val specIn  = AudioFile.readSpec(fIn)
    import specIn.{numChannels, sampleRate}
    require (numChannels == 1)
    val fOut        = file("/data/temp/limiter-test.aif")

    val boost       = 18.0.dbAmp
    val ceiling     = -0.2.dbAmp
    val attackDur   = 0.02
    val releaseDur  = 0.2

    val in0         = AudioFileIn(fIn.toURI, numChannels = specIn.numChannels)
    val in          = in0 * boost
    val attack      = (attackDur  * sampleRate).toInt
    val release     = (releaseDur * sampleRate).toInt
    val gain        = Limiter(in = in, attack = attack, release = release, ceiling = ceiling)
    val sig         = BufferMemory(in, attack + release) * gain
    val out         = AudioFileOut(sig, fOut.toURI, AudioFileSpec(numChannels = numChannels, sampleRate = sampleRate))
    Progress(out / specIn.numFrames.toDouble, Metro(sampleRate))
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