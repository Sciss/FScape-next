package de.sciss.fscape

object SlidingPercentileTest extends App {
  val g = Graph {
    import graph._
    val in = ValueIntSeq(5, 10, 12, 9, 12, 16, 25, 12, 20, 15, 11, 12, 13, 24, 28, 24, 4, 18, 6, 0)
    val m  = SlidingPercentile(in, size = 3, frac = 1)
    RepeatWindow(m).poll(Metro(2), "out")
  }

  stream.Control().run(g)
}
