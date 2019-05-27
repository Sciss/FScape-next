package de.sciss.fscape.tests

import de.sciss.fscape.{Graph, graph, stream}

object ValueSeqTest extends App {
  lazy val g = Graph {
    import graph._
    val xs = ValueSeq(1, -1, 2, -1, 3, -1, 4, -1, 5, -1)
    xs.poll(Metro(2), "SEQ")
  }

  val config = stream.Control.Config()
  config.useAsync   = false
  val ctrl = stream.Control(config)
  ctrl.run(g)

}