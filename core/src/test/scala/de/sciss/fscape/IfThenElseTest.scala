package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.synth.io.AudioFileSpec

import scala.swing.Swing

object IfThenElseTest extends App {
  val g = Graph {
    import graph._
    val sr  = 44100.0
    val in  = LFSaw(freqN = 441 / sr).take((sr * 4).toLong)
    val p1  = WhiteNoise() > 0
    val p2  = WhiteNoise() > 0
    val out = If (p1) Then {
      in
    } ElseIf (p2) Then {
      in.squared
    } Else {
      HPF(in, freqN = (441 * 3) / sr)
    }
    val fOut = userHome / "Documents" / "temp" / "if-then.aif"
    AudioFileOut(out, fOut, AudioFileSpec(numChannels = 1, sampleRate = sr))
  }

  val config = stream.Control.Config()
  config.useAsync   = false // for debugging
  val ctrl  = stream.Control(config)

  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}
