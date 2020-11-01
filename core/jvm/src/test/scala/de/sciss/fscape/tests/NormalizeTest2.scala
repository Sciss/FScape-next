package de.sciss.fscape.tests

import de.sciss.audiofile.AudioFileSpec
import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control
import de.sciss.fscape.{GE, Graph, graph}
import de.sciss.numbers.Implicits._

import scala.swing.Swing

object NormalizeTest2 extends App {
//  val fIn   = file("/data") / "projects" / "Imperfect" / "anemone" / "rec" / "capture.aif"
//  val fOut  = File.createTemp(suffix = "_killme.aif")
//  val fOut  = userHome / "Music" / "work" / "_killme.aif"
  val dir   = File.createTemp(directory = true)
  val fOut  = dir / "non-existing" / "_killme.aif"

  val g = Graph {
    import graph._
//    val in = AudioFileIn("file-in")
    val in = WhiteNoise(0.1).take(10000) ++ 0.2 ++ WhiteNoise(0.1).take(10000) // AudioFileIn(fIn, numChannels = 2)

    def normalize(in: GE): GE = {
      val abs       = in.abs
      val run       = RunningMax(abs)
//      ChannelProxy(run, 0).poll(Metro(44100), "run-max")
//      Length(run).poll(0, "run-len")
      val max       = run.last
      max /*.ampDb*/.poll("max")
      val headroom  = -0.2.dbAmp
      val gain      = max.reciprocal * headroom
      val buf       = BufferDisk(in)
      val sig       = buf * gain
//      ChannelProxy(sig, 0).poll(Metro(44100), "norm")
      sig
    }

    // Progress()
    val sig    = normalize(in) // (lap)
//    Length(sig.take(0)).poll("len")
//    val sigLen = Length(lap)
    /*val out    =*/ AudioFileOut(file = fOut, spec = AudioFileSpec(numChannels = 1 /* 2 */, sampleRate = 44100), in = sig)
//    val out = AudioFileOut("file-out", in = sig)
//    Progress(out / (2 * sigLen), Metro(44100), "normalize")
  }


  val config = Control.Config()
  config.useAsync = false
  implicit val ctrl: Control = Control(config)
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}
