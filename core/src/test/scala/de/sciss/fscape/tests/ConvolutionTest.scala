package de.sciss.fscape
package tests

import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object ConvolutionTest extends App {
//  showStreamLog = true

  val g = Graph {
    import graph._
    val dirac: GE = DC(0.9).take(2) ++ DC(0.0).take(2)
    val conv = Convolution(dirac, dirac, kernelLen = 2)
    Length(conv).poll(0, "length")
//    RepeatWindow(dirac).poll(Metro(2), "in  ")
    RepeatWindow(conv ).poll(Metro(2), "conv")
    Plot1D(conv, size = 5)
  }

  val ctrl = stream.Control()

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  ctrl.run(g)
}