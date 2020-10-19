package de.sciss.fscape.tests

import de.sciss.fscape.{Graph, graph, stream}

object GateTest extends App {
  val g = Graph {
    import graph._
    val sz   = 1024
    val gen  = SinOsc(1.0/600).take(10000)
    val tr   = Metro(64)
    val sh   = Gate(gen, tr)
    Plot1D(sh, size = sz, label = "gate")
    Length(sh).poll(0, "len")
  }

  stream.Control().run(g)
}