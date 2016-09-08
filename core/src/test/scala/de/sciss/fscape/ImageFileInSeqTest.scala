package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object ImageFileInSeqTest extends App {
  val width   = 1920
  val height  = 1080
  val indices = Seq[GE](246, 750)
  val fIn     = userHome / "Documents" / "projects" / "Imperfect" / "material" / "frame-%d.png"
  val fOut    = userHome / "Documents" / "temp" / "test2.jpg"

  val g = Graph {
    import graph._
//    val idxSeq  = Dseq(indices)
    val idxSeq  = indices.reduce(_ ++ _)
    val in      = ImageFileSeqIn(template = fIn, numChannels = 3, indices = idxSeq)
    val sig     = in
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