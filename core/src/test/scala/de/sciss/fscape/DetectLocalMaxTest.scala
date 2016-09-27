package de.sciss.fscape

import de.sciss.fscape.stream.Control

object DetectLocalMaxTest extends App {
  val g = Graph {
    import graph._
    val sz      = 1024
    val period  = 100
    val block   = 2 * period + 1
    val gen     = SinOsc(1.0/period).take(sz) * Line(0.5, 1.0, sz)
    val det     = DetectLocalMax(gen, size = block)
    Plot1D(det, size = sz, label = "Detect")
  }

  val config        = Control.Config()
  config.useAsync   = false
  config.blockSize  = 10
  Control(config).run(g)
}