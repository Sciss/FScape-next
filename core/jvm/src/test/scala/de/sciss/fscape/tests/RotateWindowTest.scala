package de.sciss.fscape.tests

import de.sciss.fscape.{Graph, graph, stream}

object RotateWindowTest extends App {
  val g = Graph {
    import graph._
    def sin = SinOsc(1.0 / 8).take(16384)
    val rL  = RotateWindow(sin, size = 24, amount = -2)
    val rR  = RotateWindow(sin, size = 24, amount = +2)
    Plot1D(sin, 72, "sin")
    Plot1D(rL , 72, "rL")
    Plot1D(rR , 72, "rR")
  }

  val ctrl  = stream.Control()

  ctrl.run(g)
  import ctrl.config.executionContext
  ctrl.status.foreach { _ =>
    sys.exit()
  }
}
