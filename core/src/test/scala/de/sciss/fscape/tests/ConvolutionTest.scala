package de.sciss.fscape
package tests

import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object ConvolutionTest extends App {
//  showStreamLog = true
  stream.Convolution.DEBUG_FORCE_FFT = true

  val g = Graph {
    import graph._
    val dirac: GE = DC(1.0).take(3) ++ DC(0.0).take(3)
//    val dirac: GE = DC(0.0).take(1) ++ DC(1.0).take(3) ++ DC(0.0).take(2)
//    val dirac: GE = DC(0.0).take(1) ++ DC(1.0).take(1) ++ DC(0.0).take(1)
    val conv = Convolution(RepeatWindow(dirac, 6, 4), dirac, kernelLen = 3)
    Length(conv).poll(0, "length")
//    RepeatWindow(dirac).poll(Metro(2), "in  ")
    RepeatWindow(conv ).poll(Metro(2), "conv")
    Plot1D(conv, size = 100)
  }

  val cfg   = stream.Control.Config()
  cfg.blockSize = 6 // XXX TODO produces problems: 5
  val ctrl  = stream.Control(cfg)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  ctrl.run(g)
}