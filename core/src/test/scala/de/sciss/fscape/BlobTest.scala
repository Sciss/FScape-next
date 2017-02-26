package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object BlobTest extends App {
  val fIn     = userHome / "Documents" / "temp" / "blob_input.jpg"
  val width   = 633
  val height  = 526

  val g = Graph {
    import graph._
    val in    = ImageFileIn(file = fIn, numChannels = 1)
    val blobs = Blobs2D(in = in, width = width, height = height, thresh = 0.3)

    def printAll(sig: GE, label: String): Unit = {
      val dup = sig zip sig // ResizeWindow(sig, 1, 0, 1)
      dup.poll(Metro(2), label)
    }

    def printOne(sig: GE, label: String): Unit = {
      sig.poll(0, label)
    }

    printOne(blobs.numBlobs, "num-blobs")

//    printAll(blobs.xMin, "x-min")
//    printAll(blobs.xMax, "x-max")
//    printAll(blobs.yMin, "y-min")
//    printAll(blobs.yMax, "y-max")

    printOne(blobs.xMin, "x-min[0]")
    printOne(blobs.xMax, "x-max[0]")
    printOne(blobs.yMin, "y-min[0]")
    printOne(blobs.yMax, "y-max[0]")

    printOne(blobs.xMin.drop(1), "x-min[1]")
    printOne(blobs.xMax.drop(1), "x-max[1]")
    printOne(blobs.yMin.drop(1), "y-min[1]")
    printOne(blobs.yMax.drop(1), "y-max[1]")

//    Length(blobs.xMin).poll(0, "num-x-min")
  }

  val config = stream.Control.Config()
  config.useAsync = false // for debugging
  val ctrl = stream.Control(config)

  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}