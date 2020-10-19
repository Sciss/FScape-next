package de.sciss.fscape.tests

import de.sciss.fscape.{Graph, graph, stream}

object SlidingWindowPercentileTest extends App {
  val g = Graph {
    import graph._

    val in = ValueIntSeq(
       4, 13, 10,  9,   //  4, 13, 10,  9
       8, 16, 23, 10,
       7,  0,  8, 23,   //  7, 13, 10, 10
      22, 26,  2,  5,   //  8, 16,  8, 10
      17, 19, 16, 10,   // 17, 19,  8, 10
      13,  2, 13, 23,   // 17, 19, 13, 10
       8, 21, 24,  8,   // 13, 19, 16, 10
       1, 19, 22, 26,   //  8, 19, 22, 23
       7, 17, 15, 27,   //  7, 19, 22, 26
      29,  3,  6, 15,   //  7, 17, 15, 26
      22, 14,  0, 25,   // 22, 14,  6, 25
      17, 24, 23,  4    // 22, 14,  6, 15
    )

    val m  = SlidingWindowPercentile(in, winSize = 4, medianLen = 3, frac = 0.5)
    m.drop(8).poll(1, "out")
//    Length(m).poll("m.length")
  }

  stream.Control().run(g)
}
