package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.{GE, Graph, graph, stream}

import scala.swing.Swing

object ImageFileInSeqTest extends App {
  val width   = 1920
  val height  = 1080
  val indices = Seq[GE](246, 750)
  val base    = {
    val b0 = userHome / "Documents" / "projects" / "Imperfect"
    if (b0.isDirectory) b0 else file("/data")  / "projects" / "Imperfect"
  }
  val fIn     = base / "material" / "frame-1.png"
  val fOut    = userHome / "Documents" / "temp" / "test2.jpg"

  val g = Graph {
    import graph._
//    val idxSeq  = Dseq(indices)
    val idxSeq  = indices.reduce(_ ++ _)
    val in      = ImageFileSeqIn(template = fIn, numChannels = 3, indices = idxSeq)
    val sig     = in
//    {
//      val in1 = in.take(width * height)
//      val in2 = in.drop(width * height)
//      val inB = BufferMemory(in1, width * height)
//      in2 atan2 inB
//    }
    val spec    = ImageFile.Spec(width = width, height = height * indices.size, numChannels = 3 /* 1 */,
      fileType = ImageFile.Type.JPG, sampleFormat = ImageFile.SampleFormat.Int8)
    ImageFileOut(file = fOut, spec = spec, in = sig)
  }

  val config = stream.Control.Config()
  config.blockSize  = 599   // a prime number unrelated to `width` and `height`, for testing
  config.useAsync   = false // for debugging
  val ctrl  = stream.Control(config)

  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}