package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control
import de.sciss.fscape.{Graph, graph}

import scala.Predef.{any2stringadd => _, require}
import scala.swing.Swing

object AffineTest2 {
  final case class Config(kernel: Int = 16, noiseAmp: Double = 0.1, width: Int = 512, height: Int = 512)

  def main(args: Array[String]): Unit = run(Config())

  def run(config: Config): Unit = {
    import config._
    val dir0      = {
      val d = userHome / "Documents" / "projects" / "Imperfect"
      if (d.isDirectory) d else file("/data") / "projects" / "Imperfect"
    }
    val dir       = dir0 / "scans" /"notebook2016"
    val fIn1      = dir / "universe-test1q.png"
    val fOut      = file("/") / "data" / "temp" /"test.png"

    require (fIn1.isFile)

    var gui: SimpleGUI = null
    val cfg = Control.Config()
    cfg.progressReporter = p => Swing.onEDT(gui.progress = p.total)

    val g = Graph {
      import graph._
      val i10       = ImageFileIn(fIn1, numChannels = 4)
      val i1        = ChannelProxy(i10, 0)
      val m1        = MatrixInMatrix(i1, rowsOuter = height, columnsOuter = width, rowsInner = kernel, columnsInner = kernel)
      val m1a       = AffineTransform2D.scale(in = m1, widthIn = kernel, heightIn = kernel,
        sx = 0.9, sy = 1.0, zeroCrossings = 0)
      val sig = m1a
      val specOut   = ImageFile.Spec(width = width, height = height, numChannels = 1)
      ImageFileOut(file = fOut, spec = specOut, in = sig)
    }

    val ctl = Control(cfg)
    Swing.onEDT {
      gui = SimpleGUI(ctl)
    }

//    showStreamLog = true

    ctl.run(g)
  }
}
