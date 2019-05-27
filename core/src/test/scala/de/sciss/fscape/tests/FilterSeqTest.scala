package de.sciss.fscape.tests

import de.sciss.fscape.{Graph, graph, stream}

object FilterSeqTest extends App {
  val g = Graph {
    import graph._
    val sz   = 1024
    val gen  = SinOsc(1.0/600)
    val sh   = FilterSeq(gen, gen > 0.99)
    Plot1D(sh, size = sz, label = "filter")
  }

  stream.Control().run(g)
}