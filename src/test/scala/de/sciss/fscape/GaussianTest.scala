package de.sciss.fscape

object GaussianTest extends App {
  val g = Graph {
    import graph._
    val sz  = 1024
    val gen = GenWindow(size = sz, shape = GenWindow.Gauss)
    Plot1D(gen, size = sz, label = "Gauss")
  }

  stream.Control().run(g)
}