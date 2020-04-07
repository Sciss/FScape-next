package de.sciss.fscape.tests

import de.sciss.fscape.{Graph, graph, stream}

object LFSawTest extends App {
  def any2stringadd: Any = ()

  val g = Graph {
    import graph._
//    val gen  = LFSaw(1.0/300, phase = 0.5)
    LFSaw(1.0/800, phase = 0.0).poll(0, "first")
    LFSaw(1.0/800, phase = 0.5).poll(0, "zero")
    val gen = LFSaw(1.0/800, phase = 0.75 /* 0.25 */)
    val up  = (gen + 1) * 2
    val a   = up.min(1)
    val b   = (up - 3).max(0)
    val sig = a - b
    Plot1D(sig, size = 800, label = "LFSaw")
  }

  stream.Control().run(g)
}