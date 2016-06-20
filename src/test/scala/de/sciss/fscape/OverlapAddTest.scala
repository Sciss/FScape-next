package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.numbers
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

import scala.swing.Swing

object OverlapAddTest extends App {
  val in  = userHome / "Music" / "work" / "mentasm-1532a860.aif"
  val out = userHome / "Music" / "work" / "_killme.aif"

  val numFrames = AudioFile.readSpec(in).numFrames.toInt
  import numbers.Implicits._
  val fftSize = numFrames.nextPowerOfTwo

  lazy val g = Graph {
    import graph._
    val disk          = DiskIn(file = in, numChannels = 1)
    val disk1         = DiskIn(file = in, numChannels = 1)
    val inputWinSize  = 667 * 3 // 16384
    val stepSize      = 667
    val gain          = stepSize.toDouble / inputWinSize
    val slide         = Sliding(in = disk, size = inputWinSize, step = stepSize)
    val shiftXPad     = 0: GE
    val lap           = OverlapAdd(in = slide, size = inputWinSize, step = shiftXPad + stepSize)
    val sig0          = lap * gain
    val sig           = sig0 - disk1
    DiskOut(file = out, spec = AudioFileSpec(sampleRate = 44100.0, numChannels = 1), in = sig)
  }

  // showStreamLog = true

  val config = stream.Control.Config()
  config.useAsync = false // for debugging
  val ctrl = stream.Control(config)
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}