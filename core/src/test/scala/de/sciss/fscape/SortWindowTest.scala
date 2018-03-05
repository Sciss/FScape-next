package de.sciss.fscape

object SortWindowTest extends App {
  val config = stream.Control.Config()
  config.blockSize = 1024
  config.useAsync = false // for debugging
  val ctrl = stream.Control(config)

  lazy val g = Graph {
    import graph._
    val in0     = ArithmSeq(length = 10) % 6  // 0,  1,  2,  3,  4, 5,  0,  1,  2,  3
    val in      = ReverseWindow(in0, 5)       // 4,  3,  2,  1,  0, 3,  2,  1,  0,  5
    val sig     = SortWindow(in, -in, 5)      // 0, -1, -2, -3, -4, 0, -1, -2, -3, -5
    RepeatWindow(sig).poll(Metro(2), "sorted")
  }

  ctrl.run(g)

//  Swing.onEDT {
//    SimpleGUI(ctrl)
//  }

  println("Running.")
}