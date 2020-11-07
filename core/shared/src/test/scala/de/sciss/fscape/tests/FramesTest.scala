package de.sciss.fscape
package tests

import de.sciss.fscape.Ops._
import de.sciss.log.Level

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object FramesTest extends App {
  val g = Graph {
    import graph._
    val frames = Frames(DC(0).take(10))
    frames     .poll(0, "first")
    frames.last.poll(0, "last")
  }

  Log.stream.level = Level.Debug

  val c = stream.Control()
  c.run(g)
  Await.result(c.status, Duration.Inf)
}