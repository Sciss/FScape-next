package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.synth.io.AudioFileSpec

import scala.swing.Swing

object CdpTest extends App {
  lazy val g = Graph {
    import graph._
    val sr    = 44100.0
    val lenIn = sr * 10
    val ln    = Line(0, 1, length = lenIn)
    val freq  = ln.linexp(0, 1, 200, 4000)
    val sig   = SinOsc(freq/sr).take(lenIn) * 0.5
    val rvs   = cdp.Modify.Radical.Reverse(sig)
    val fOut  = file("/data") / "audio_work" / "reverse.aif"
    val aOut  = AudioFileOut(file = fOut, spec = AudioFileSpec(sampleRate = sr, numChannels = 1), in = rvs)
    aOut.last.poll(0, "frames-written")
  }

  val config = stream.Control.Config()
  config.useAsync   = false
  implicit val ctrl = stream.Control(config)
  //  showStreamLog = true
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }
}