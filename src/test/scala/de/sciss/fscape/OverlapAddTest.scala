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
    // val disk          = DiskIn(file = in, numChannels = 1)
    // val disk = SinOsc(10.0/44100).take(44100)
    // val disk = DC(0.5).take(2000)
    val disk = SinOsc(1.0/2000).take(3000)
    // val disk1         = DiskIn(file = in, numChannels = 1)
    val stepSize      = 100
    val inputWinSize  = stepSize * 4 // 16384
    val win           = GenWindow(size = inputWinSize, shape = GenWindow.Hann)
    val gain          = 0.5
    val numPadLeft    = inputWinSize - stepSize
    val padLeft       = DC(0.0).take(numPadLeft)
    val slideIn       = disk // spadLeft ++ disk
    val slide         = Sliding   (in = slideIn , size = inputWinSize, step = stepSize)
    val shiftXPad     = 0: GE
    val windowed      = slide * win
    val lap           = OverlapAdd(in = windowed, size = inputWinSize, step = stepSize + shiftXPad)
    val drop          = lap // .drop(numPadLeft)
    val sig0          = drop * gain
    val sig           = sig0 // - disk1
//    val sig = slide
    DiskOut(file = out, spec = AudioFileSpec(sampleRate = 44100.0, numChannels = 1), in = sig)
  }

  // showStreamLog = true

  val config = stream.Control.Config()
  config.blockSize = 1020
  config.useAsync = false // for debugging
  val ctrl = stream.Control(config)
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}