package de.sciss.fscape.tests

import de.sciss.fscape.{Graph, graph, stream}

object LatchTest extends App {
  val g = Graph {
    import graph._
    val sz   = 1024
    val gen  = SinOsc(1.0/600)
    val tr   = Metro(64)
    val sh   = Latch(gen, tr)
    Plot1D(sh, size = sz, label = "S+H")
  }

  stream.Control().run(g)
}