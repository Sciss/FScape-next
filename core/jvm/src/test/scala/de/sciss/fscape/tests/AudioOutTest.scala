package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.{Graph, graph, stream}
import de.sciss.audiofile.AudioFileSpec

import scala.swing.Swing

object AudioOutTest extends App {
  lazy val g = Graph {
    import graph._
    val sr    = 44100.0
    val sig   = DC(0.5).take(2000)
    val fOut  = userHome / "Documents" / "temp" / "test.aif"
    val aOut  = AudioFileOut(file = fOut, spec = AudioFileSpec(sampleRate = sr, numChannels = 1), in = sig)
    aOut.last.poll(0, "frames-written")
  }

  val config = stream.Control.Config()
  config.useAsync   = false
  val ctrl = stream.Control(config)
//  showStreamLog = true
  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }
}