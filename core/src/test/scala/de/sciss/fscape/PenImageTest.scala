package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control

import scala.Predef.{any2stringadd => _}
import scala.swing.Swing

object PenImageTest {
  def main(args: Array[String]): Unit = run()

  def run(): Unit = {
    val fOut = userHome / "Documents" / "temp" /"test.png"
    fOut.parent.mkdirs()

    var gui: SimpleGUI = null

    val g = Graph {
      import graph._
      val w   = 640
      val h   = 480
      val len = 8000
      val x   = SinOsc(7.2/len).linLin(-1,+1,0,w-1).roundTo(1)
      val y   = LFSaw (7.5/len).linLin(-1,+1,0,h-1).roundTo(1)
      val sig = PenImage(
        width  = w,
        height = h,
        src    = WhiteNoise(1.0).take(len),
        x      = x,
        y      = y,
//        next   = Metro(len),
        wrap   = 0
      )
      val spec = ImageFile.Spec(width = w, height = h, numChannels = 1)
      ImageFileOut(sig, fOut, spec)
    }

    val ctl = Control()
    Swing.onEDT {
      gui = SimpleGUI(ctl)
    }

    ctl.run(g)
  }
}
