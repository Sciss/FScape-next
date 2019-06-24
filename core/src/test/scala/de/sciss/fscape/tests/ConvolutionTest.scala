package de.sciss.fscape
package tests

import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object ConvolutionTest extends App {
//  showStreamLog = true

  val g = Graph {
    import graph._
    val dirac: GE = DC(1.0).take(3) ++ DC(0.0).take(3)
    val conv = Convolution(dirac, dirac, kernelLen = 3)
    Length(conv).poll(0, "length")
//    RepeatWindow(dirac).poll(Metro(2), "in  ")
    RepeatWindow(conv ).poll(Metro(2), "conv")
    Plot1D(conv, size = 9)
  }

  val ctrl = stream.Control()

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  ctrl.run(g)
}