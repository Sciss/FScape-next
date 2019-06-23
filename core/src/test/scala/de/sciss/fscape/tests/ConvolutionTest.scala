package de.sciss.fscape
package tests

import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object ConvolutionTest extends App {
  showStreamLog = true

  val g = Graph {
    import graph._
    val dirac: GE = DC(1.0).take(1)
    val conv = Convolution(dirac, dirac, kernelLen = 1)
    Length(conv).poll(0, "length")
    conv.poll(1, "value")
  }

  val ctrl = stream.Control()

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  ctrl.run(g)
}