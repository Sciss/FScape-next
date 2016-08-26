package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.synth.io.AudioFileSpec

import scala.swing.Swing

object ResampleTest extends App {
  val g = Graph {
    import graph._
    val sr    = 44100.0
    val in    = SinOsc(441/sr).take(sr.toLong * 10)
    val sig   = Resample(in = in, factor = 0.5)
    val fOut  = userHome / "Documents" / "temp" / "resample_50.aif"
    AudioFileOut(file = fOut, spec = AudioFileSpec(sampleRate = sr, numChannels = 1), in = sig)
  }

  val config = stream.Control.Config()
  config.useAsync = false
  implicit val ctrl = stream.Control(config)
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }
}