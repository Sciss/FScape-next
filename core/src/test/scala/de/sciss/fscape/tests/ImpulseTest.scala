package de.sciss.fscape.tests

import de.sciss.fscape.{Graph, graph, stream}

object ImpulseTest extends App {
  val g = Graph {
    import graph._
    val freqN   = Line(1.0/300, 1.0/50, 1000)
    val sig     = Impulse(freqN)
    Plot1D(sig, size = 1500)
  }

  stream.Control().run(g)
}