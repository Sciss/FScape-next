package de.sciss.fscape

import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control

import scala.swing.Swing

object BloodyTest extends App {
  import graph._

  lazy val g = Graph {
    val sz = 17000

    val in        = Line(0, 1, sz) // WhiteNoise() // SinOsc(freqN = 1.0/16 /* 4410/sr */, phase = 0 /* math.Pi/2 */)
    val max       = in.last // RunningMax(in).last
    val buf       = BufferDisk(in)
    val sig       = buf * max
    Length(sig).poll(0, "len")
    sig
  }

  val config = Control.Config()
  var gui: SimpleGUI = _
  config.progressReporter = rep => Swing.onEDT(gui.progress = rep.total)
  implicit val ctrl = Control(config)

  Swing.onEDT {
    gui = SimpleGUI(ctrl)
  }

  showStreamLog = true
  ctrl.run(g)

  println("Running.")
}