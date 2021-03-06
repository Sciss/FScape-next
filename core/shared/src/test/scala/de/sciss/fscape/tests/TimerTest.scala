package de.sciss.fscape
package tests

import de.sciss.fscape.Ops._

object TimerTest extends App {
  val g = Graph {
    import graph._
    val width     = 2528
    val height    = 1288
    val frameSize = width * height
    val medianLen = 7
    val period    = frameSize.toLong * medianLen
    val tr        = Metro(period)
    val timer     = Timer(tr)
    val t1        = (0: GE) ++ timer
    val diff      = timer - t1
    val pollTrig  = diff < 0
    t1.take(frameSize.toLong * medianLen * 100).poll(pollTrig, "report")
  }

  stream.Control().run(g)
}