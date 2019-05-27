package de.sciss.fscape.tests

import de.sciss.fscape.stream.Control
import de.sciss.fscape.{Graph, graph}

object DetectLocalMaxTest extends App {
  val g = Graph {
    import graph._
    val sz      = 1024
    val period  = 100
    val block   = 2 * period + 1
    val gen     = SinOsc(1.0/period).take(sz) * Line(0.5, 1.0, sz)
//    val gen     = Metro(period, period/2).take(sz) * Line(0.5, 1.0, sz)
    val det     = DetectLocalMax(gen, size = block)
    Plot1D(det, size = sz, label = "Detect")
  }

  val config        = Control.Config()
  config.useAsync   = false
  config.blockSize  = 1024    // problem is independent of block-size
  println("--1")
  Control(config).run(g)
  println("--2")
}