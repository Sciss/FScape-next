package de.sciss.fscape

import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

/** Tests function of `stepSize > winSize` */
object SlidingTest extends App {
  val config = stream.Control.Config()
  config.blockSize = 1024
  config.useAsync = false // for debugging
  val ctrl = stream.Control(config)

  lazy val g = Graph {
    import graph._
    val sig     = WhiteNoise()
    val sliding = Sliding(sig, size = 4, step = 16)
    sliding.take(2000).poll(500, "foo")
  }

  // showStreamLog = true

  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}