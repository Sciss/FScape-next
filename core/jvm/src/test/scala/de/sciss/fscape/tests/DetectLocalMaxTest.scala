package de.sciss.fscape
package tests

import de.sciss.fscape.Ops._
import de.sciss.fscape.stream.Control

object DetectLocalMaxTest extends App {
  val g = Graph {
    import graph._
    val sz      = 1024
    val period  = 100
    val block   = 2 * period + 1
    val gen     = SinOsc(1.0/period).take(sz) * Line(0.5, 1.0, sz)
//    val gen     = Metro(period, period/2).take(sz) * Line(0.5, 1.0, sz)
    val det     = DetectLocalMax(gen, size = block)
    Frames(det).poll(det, "tr")
    Plot1D(gen, size = sz, label = "gen")
    Plot1D(det, size = sz, label = "Detect")
//    Length(det).poll("len")
  }

  val config        = Control.Config()
  config.useAsync   = false
  config.blockSize  = 1024    // problem is independent of block-size
  println("--1")
  Control(config).run(g)
  println("--2")
}