package de.sciss.fscape

object TimerTest extends App {
  val g = Graph {
    import graph._
    val width     = 2528
    val height    = 1288
    val frameSize = width * height
    val medianLen = 7
    val period    = frameSize.toLong * medianLen
    val tr        = ImpulseFOO(period)
    val timer     = Timer(tr)
    val t1        = (0: GE) ++ timer
    val diff      = timer - t1
    val pollTrig  = diff < 0
    t1.take(frameSize.toLong * medianLen * 100).pollFOO(pollTrig, "report")
  }

  stream.Control().run(g)
}