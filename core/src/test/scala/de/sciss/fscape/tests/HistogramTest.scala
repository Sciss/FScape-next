package de.sciss.fscape.tests

import de.sciss.fscape.stream.Control
import de.sciss.fscape.{Graph, graph}

object HistogramTest extends App {
  val g = Graph {
    import graph._
    val sz      = 1024
    val period  = 100
    val gen     = SinOsc(1.0/period).take(sz) // * Line(0.5, 1.0, sz)
    val h       = Histogram(gen, bins = 16, lo = -1.0, hi = +1.0)
    Plot1D(h, size = sz, label = "Histogram")
  }

  val config        = Control.Config()
  config.useAsync   = false
  config.blockSize  = 1024    // problem is independent of block-size
  println("--1")
  Control(config).run(g)
  println("--2")
}