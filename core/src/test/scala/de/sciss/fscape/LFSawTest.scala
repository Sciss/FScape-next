package de.sciss.fscape

object LFSawTest extends App {
  val g = Graph {
    import graph._
    val gen  = LFSaw(1.0/300, phase = 0.5)
    Plot1D(gen, size = 600, label = "LFSaw")
  }

  stream.Control().run(g)
}