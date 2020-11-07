package de.sciss.fscape
package tests

import de.sciss.fscape.Ops._
import de.sciss.fscape.stream.Control.Config

object OnePoleTest extends App {
  val g = Graph {
    import graph._
    val sz    = 1024
    val gen   = WhiteNoise().take(sz)
    val sig   = OnePole(gen, 0.95)
    Plot1D(sig, size = sz, label = "filter")
  }

  val cfg = Config()
  cfg.blockSize = 64
  stream.Control(cfg).run(g)
}