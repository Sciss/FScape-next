package de.sciss.fscape.tests

import de.sciss.fscape.{Graph, graph, stream}

object SetResetFFTest extends App {
  val g = Graph {
    import graph._
    val sz   = 1024
    val tr   = Metro(64)
    val reset= Metro(72)
    val sh   = SetResetFF(tr, reset)
    Plot1D(sh, size = sz, label = "SetResetFF")
  }

  stream.Control().run(g)
}