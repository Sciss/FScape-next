package de.sciss.fscape
package tests

import de.sciss.fscape.Ops._
import de.sciss.fscape.stream.Control

object HistogramTest extends App {
  val config        = Control.Config()
  config.useAsync   = false
  config.blockSize  = 128 // 1024

  val g = Graph {
    import graph._
    val sz      = config.blockSize // 1024
    val period  = 1024 // 100
    val bins    = 16 // 256 // 200
//    val gen0    = SinOsc(1.0/period)
    val gen0    = LFSaw(1.0/period)
    val gen     = gen0.take(sz) // * Line(0.5, 1.0, sz)
    val h0      = Histogram(gen, bins = bins, lo = -1.0, hi = +1.0, mode = 0)
    val h       = h0 // ResizeWindow(h0, 17 * 10, stop = -17 * 9)
    Plot1D(h, size = bins /*sz*/, label = "Histogram")
  }

//  fscape.showStreamLog = true
  Control(config).run(g)
}