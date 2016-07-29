package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.synth.io.AudioFileSpec

import scala.swing.Swing

object OffsetOverlapAddTest extends App {
  val out = userHome / "Music" / "work" / "_killme.aif"

  val config = stream.Control.Config()
  config.blockSize = 1024
  config.useAsync = false // for debugging
  val ctrl = stream.Control(config)

  lazy val g = Graph {
    import graph._
    val stepSize      = 100
    val overlap       = 2
    val winSize       = stepSize * overlap
    val impulse       = Impulse(1.0/winSize).take(3000)   // 'distribute diracs'
    val offset        = WhiteNoise(4).floor // XXX TODO --- a `.toInt` would be useful
    val minOffset     = -4
    val sig           = OffsetOverlapAdd(in = impulse, size = winSize, step = stepSize,
      offset = offset, minOffset = minOffset)
    AudioFileOut(file = out, spec = AudioFileSpec(sampleRate = 44100.0, numChannels = 1), in = sig)
  }

  // showStreamLog = true

  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}