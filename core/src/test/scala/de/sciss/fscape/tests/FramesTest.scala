package de.sciss.fscape.tests

import de.sciss.fscape.{Graph, graph, showStreamLog, stream}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object FramesTest extends App {
  val g = Graph {
    import graph._
    val frames = Frames(DC(0).take(10))
    frames     .poll(0, "first")
    frames.last.poll(0, "last")
  }

  showStreamLog = true

  val c = stream.Control()
  c.run(g)
  Await.result(c.status, Duration.Inf)
}