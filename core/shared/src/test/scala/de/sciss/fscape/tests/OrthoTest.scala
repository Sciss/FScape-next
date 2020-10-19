package de.sciss.fscape.tests

import de.sciss.fscape.{Graph, graph, stream}

// XXX TODO --- make a ScalaTest suite
object OrthoTest extends App {
  val g = Graph {
    import graph._
    val v1 = Vector(3.0, 1.0)
    val v2 = Vector(2.0, 2.0)
    val vs = ValueDoubleSeq(v1 ++ v2: _*)
    val us = GramSchmidtMatrix(vs, columns = 2, rows = 2, normalize = 0)
    val es = GramSchmidtMatrix(vs, columns = 2, rows = 2, normalize = 1)
    RepeatWindow(us).poll(2, "us") // expected: (3, 1), (-0.4, 1.2)
    RepeatWindow(es).poll(2, "es") // expected: (0.949, 0.316), (-0.316, 0.949)
  }

  stream.Control().run(g)
}