package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.{Graph, graph, stream}

import scala.Predef.{any2stringadd => _}

// issue #24
object AffineTest3 {
  def main(args: Array[String]): Unit = run()

  def run(): Unit = {
    val startFrame  = 3
    val endFrame    = 139
    val numFrames   = endFrame - startFrame + 1
    val wIn         = 3280
    val hIn         = 2464
    val outDir      = file("/data/temp/exposure-test")
    outDir.mkdirs()

    val g = Graph {
      import graph._
      val tempIn      = file("/data/projects/Maeanderungen/exposure/site-13/frame-%d.jpg")
      val indicesIn   = ArithmSeq(start = startFrame, length = numFrames)
      val imgIn       = ImageFileSeqIn(tempIn.toURI, numChannels = 3, indices = indicesIn)
      val hOut        = hIn / 16
      val wOut        = wIn / 16
      val sx          = 1.0 / 16
      val sy          = 1.0 / 16
      val small       = AffineTransform2D.scale(imgIn,
        widthIn   = wIn , heightIn  = hIn,
        widthOut  = wOut, heightOut = hOut,
        sx = sx, sy = sy)
      val tempOut     = outDir / "frame-%d.png"
      val indicesOut  = ArithmSeq(start = 1, length = numFrames)
      val specOut     = ImageFile.Spec(width = wOut, height = hOut, numChannels = 3)
      ImageFileSeqOut(small, tempOut.toURI, specOut, indices = indicesOut)
    }

    val config        = stream.Control.Config()
//    config.blockSize  = wIn/16 * hIn/16
    val ctrl          = stream.Control(config)

    ctrl.run(g)
  }
}
