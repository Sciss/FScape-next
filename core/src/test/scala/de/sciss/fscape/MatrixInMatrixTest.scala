package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control

import scala.swing.Swing

object MatrixInMatrixTest extends App {
  var gui: SimpleGUI = _
  val cfg       = Control.Config()
  cfg.useAsync  = false
  cfg.progressReporter = p => Swing.onEDT(gui.progress = p.total)

  val baseDir = {
    val tmp = file("/data")
    if (tmp.isDirectory) tmp else userHome / "Documents"
  }

  val g = Graph {
    import graph._
    val fIn1    = baseDir / "projects" / "Imperfect" / "scans" / "notebook2016" / "universe-test1q.png"
    val fIn2    = baseDir / "projects" / "Imperfect" / "scans" / "notebook2016" / "universe-test2q.png"
    val fOut    = baseDir / "temp" / "test.png"
    val i1      = ImageFileIn(fIn1, numChannels = 3)
    val i2      = ImageFileIn(fIn2, numChannels = 3)
    val width   = 512
    val height  = 512
    val frameSize = width * height
    val kernel  = 32
    val kernelS = kernel * kernel
    val m1      = MatrixInMatrix(i1, rowsOuter = height, columnsOuter = width, rowsInner = kernel, columnsInner = kernel)
    val m2      = MatrixInMatrix(i2, rowsOuter = height, columnsOuter = width, rowsInner = kernel, columnsInner = kernel)
    // Length(m1).poll(0, "m")
//    val p       = Metro(kernelS)
//    val avg     = RunningSum(m1, p) / kernelS
//    val flt     = ResizeWindow(avg, size = kernelS, start = kernelS - 1)
//    Length(flt).poll(0, "flt")
    
    val m1f       = Real2FFT(m1, rows = kernel, columns = kernel)
    val m2f       = Real2FFT(m2, rows = kernel, columns = kernel)
    val m3f       = m1f.complex * m2f
    val m3        = Real2IFFT(m3f, rows = kernel, columns = kernel)
    val flt       = ResizeWindow(m3, size = kernelS, start = kernelS - 1)

    Progress(Frames(flt) / (2 * frameSize), Metro(width))
    Length(flt).poll(0, "flt-len")

    val i3        = flt
    val frameTr1  = Metro(frameSize)
    val frameTr2  = Metro(frameSize)
    val maxR      = RunningMax(i3, trig = frameTr1).drop(frameSize - 1)
    val minR      = RunningMin(i3, trig = frameTr1).drop(frameSize - 1)
    val max       = Gate(maxR, gate = frameTr2)
    val min       = Gate(minR, gate = frameTr2)
    val mul       = (max - min).reciprocal
    val add       = -min
    val i3e       = i3.elastic(frameSize / cfg.blockSize + 1)

    val noise     = WhiteNoise(0.1)
    val i4        = (i3e + add) * mul + noise

    Progress(Frames(i4) / (2 * frameSize), Metro(width))

    val sig     = i4.clip(0.0, 1.0)
    val specOut = ImageFile.Spec(width = width, height = height, numChannels = 3)
    ImageFileOut(file = fOut, spec = specOut, in = sig)
  }

  val ctl = Control(cfg)
  Swing.onEDT {
    gui = SimpleGUI(ctl)
  }
  ctl.run(g)
}