package de.sciss.fscape

object FramesTest extends App {
  val g = Graph {
    import graph._
    val frames = Frames(DC(0).take(10))
    frames.poll(0, "first")
    frames.last.poll(0, "last")
  }

  stream.Control().run(g)
}