package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control
import de.sciss.synth.io.AudioFile

import scala.swing.Swing

object LoudnessTest extends App {
  lazy val g = Graph {
    import graph._
//    val name    = "13533_beesLoop.aif"
//    val name    = "18667_beesLoop.aif"

//    val name    = "19178_beesLoop.aif"
//    val fIn     = file("/data/projects/Schwaermen/audio_work/for_pi/loops/") / name

    val fIn     = file("/data/projects/Wr_t_ngM_ch_n_/database1/db000055.aif")

    val specIn  = AudioFile.readSpec(fIn)
    import specIn.{numChannels, sampleRate}
    val in      = AudioFileIn(fIn, numChannels = specIn.numChannels) * 6.724812881902514
    val inMono  = if (numChannels == 1) in else ChannelProxy(in, 0) + ChannelProxy(in, 1)
    val winSize = sampleRate.toInt
    val inSlid  = Sliding(inMono, size = winSize, step = winSize/2)
//    val inSlid  = DC(0).take(specIn.numFrames)
//    val inSlid  = WhiteNoise(0.001).take(specIn.numFrames)
    val loud    = Loudness(in = inSlid, sampleRate = sampleRate, size = winSize)
    val sum     = RunningSum(loud)
    val num     = Length(sum)
    val avg     = sum.last / num
    avg.poll(0, "avg")


    /*

    val scl1 = ((dif1.pow(0.85) - 0) * 1.28).pow(1.0).dbamp // 3.1148602768766525

val dif8 = tgt - avg8
val scl8 = ((dif8.abs.pow(0.85) * dif8.signum - 0.0) * 1.28).pow(1.0).dbamp // 7.549001971705424


     */

    RepeatWindow(loud).poll(Metro(2), "phon")

    // Progress(out / specIn.numFrames.toDouble, Metro(44100))
  }

  val config = Control.Config()
  config.useAsync = false
  implicit val ctrl: Control = Control(config)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  ctrl.run(g)
}