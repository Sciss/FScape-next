package de.sciss.fscape.tests

import de.sciss.fscape.stream.Control.Config
import de.sciss.fscape.{Graph, graph, stream}

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