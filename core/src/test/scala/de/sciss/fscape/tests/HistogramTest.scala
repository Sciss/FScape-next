package de.sciss.fscape.tests

import de.sciss.fscape.stream.Control
import de.sciss.fscape.{Graph, graph}

object HistogramTest extends App {
  val g = Graph {
    import graph._
    val sz      = 1024
    val period  = 1024 // 100
    val bins    = 200
//    val gen0    = SinOsc(1.0/period)
    val gen0    = LFSaw(1.0/period)
    val gen     = gen0.take(sz) // * Line(0.5, 1.0, sz)
    val h0      = Histogram(gen, bins = bins, lo = -1.0, hi = +1.0, mode = 0)
    val h       = h0 // ResizeWindow(h0, 17 * 10, stop = -17 * 9)
    Plot1D(h, size = bins /*sz*/, label = "Histogram")
  }

  val config        = Control.Config()
  config.useAsync   = false
  config.blockSize  = 1024    // problem is independent of block-size
//  fscape.showStreamLog = true
  println("--1")

  Control(config).run(g)
  println("--2")
}