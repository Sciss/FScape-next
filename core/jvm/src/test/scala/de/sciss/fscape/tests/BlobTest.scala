package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.{GE, Graph, graph, stream}

import scala.swing.Swing

object BlobTest extends App {
  val fIn     = file("/") / "data" / "temp" / "blob_input.png"
  val specIn  = ImageFile.readSpec(fIn.toURI)
  val width   = specIn.width  // 1920 // 633
  val height  = specIn.height // 1080 // 526

  require (fIn.isFile)

  val g = Graph {
    import graph._
    val in    = 1.0 - ImageFileIn(file = fIn.toURI, numChannels = 1)
    val blobs = Blobs2D(in = in, width = width, height = height, thresh = 0.3)

    def printAll(sig: GE, label: String): Unit =
      sig.poll(1, label)

//    def printOne(sig: GE, label: String): Unit = {
//      sig.poll(0, label)
//    }

    printAll(blobs.numBlobs   , "num-blobs   ")
    printAll(blobs.bounds     , "bounds      ")
    printAll(blobs.numVertices, "num-vertices")
//    printAll(blobs.vertices   , "vertices    ")

//    printOne(blobs.xMin, "x-min[0]")
//    printOne(blobs.xMax, "x-max[0]")
//    printOne(blobs.yMin, "y-min[0]")
//    printOne(blobs.yMax, "y-max[0]")
//
//    printOne(blobs.xMin.drop(1), "x-min[1]")
//    printOne(blobs.xMax.drop(1), "x-max[1]")
//    printOne(blobs.yMin.drop(1), "y-min[1]")
//    printOne(blobs.yMax.drop(1), "y-max[1]")

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