package de.sciss.fscape
package tests

import de.sciss.fscape.Ops._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control

import scala.Predef.{any2stringadd => _}
import scala.swing.Swing

// XXX TODO: currently throws NPE
object SlicesDebug {
  def main(args: Array[String]): Unit = run()

  def any2stringadd: Any = ()

  def run(): Unit = {
    var gui: SimpleGUI = null
    val cfg = Control.Config()

    val g = Graph {
      import graph._
      val chunkSize     = 4096  // 65536
      val numChunks     = 128   // 176
      val numFramesIn   = numChunks * chunkSize
      val in0           = DC(0.0).take(numFramesIn)
      val sliceIndices  = Seq[GE](0, numChunks - 1)
      val spanStart     = sliceIndices.reduce(_ ++ _) * chunkSize
      val spanStop      = spanStart + chunkSize
      val spans         = spanStart zip spanStop

      val slices = Slices(in0, spans)
      Length(slices).poll(0, "length")
    }

    val ctl = Control(cfg)
    Swing.onEDT {
      gui = SimpleGUI(ctl)
    }
    ctl.run(g)
  }
}