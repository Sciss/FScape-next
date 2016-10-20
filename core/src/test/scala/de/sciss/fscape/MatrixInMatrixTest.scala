package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.stream.Control

object MatrixInMatrixTest extends App {
  val g = Graph {
    import graph._
    val fIn     = userHome / "Documents" / "projects" / "Imperfect" / "scans" / "notebook2016" / "universe-test1q.png"
    val fOut    = userHome / "Documents" / "temp" / "test.png"
    val in      = ImageFileIn(fIn, numChannels = 3)
    val width   = 512
    val height  = 512
    val kernel  = 32
    val kernelS = kernel * kernel
    val m       = MatrixInMatrix(in, rowsOuter = height, columnsOuter = width, rowsInner = kernel, columnsInner = kernel)
    Length(m).poll(0, "m")
    val p       = Metro(kernelS)
    val avg     = RunningSum(m, p) / kernelS
    val flt     = ResizeWindow(avg, size = kernelS, start = kernelS - 1)
    Length(flt).poll(0, "flt")
    val specOut = ImageFile.Spec(width = width, height = height, numChannels = 3)
    ImageFileOut(fOut, specOut, in = flt)
  }

  val cfg       = Control.Config()
  cfg.useAsync  = false

  Control(cfg).run(g)
}