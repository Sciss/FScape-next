package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control
import de.sciss.fscape.{GE, Graph, graph}
import de.sciss.numbers

import scala.Predef.{any2stringadd => _, _}
import scala.swing.Swing

object AffineDebug {
  val baseDir: File = userHome / "Documents" / "projects" / "Imperfect" / "scans" /"notebook2016"

  final case class Config(kernel: Int = 16, noiseAmp: Double = 0.05,
                          groupIdx: Int = -1, fadeFrames: Int = 24 * 2 /* * 14 */, skipFrames: Int = 0,
                          zeroCrossings: Int = 0,
                          lagTime: Double = 1.0 - 1.0/24, fFltIn: File = baseDir / s"hp5-fft2d-16.aif")

  def main(args: Array[String]): Unit = {
    run(Config())
  }

  def format(f: File, i: Int): File = {
    val n = f.name.format(i)
    f.parentOption.fold(file(n))(_ / n)
  }

  def run(config: Config): Unit = {
    import config._

    var gui: SimpleGUI = null
    val cfg = Control.Config()
    cfg.useAsync          = false
    cfg.blockSize         = 256
    cfg.progressReporter  = p => Swing.onEDT(gui.progress = p.total)

    val g = Graph {
      import graph._
      val kernelS   = kernel * kernel

      val periodFrames = fadeFrames * 4

      def mkSawLow(phase: Double): GE = {
        val lfSaw   = LFSaw(1.0/periodFrames, phase = phase)
        val lfSawUp = (lfSaw + (1: GE)) * 2
        val low0    = lfSawUp.min(1) - (lfSawUp - 3).max(0)
        val low     = if (skipFrames == 0) low0 else low0.drop(skipFrames)
        low
      }

      val env1Mat   = mkSawLow(0.75)

      val m1        = {
        val tempIn1 = userHome / "Documents" / "temp" / "notebook-test.png"
        require (tempIn1.isFile)
        val img0 = ImageFileIn(tempIn1, numChannels = 3)
        RepeatWindow(img0, size = kernelS, num = 100)
      }

      import numbers.Implicits._
      //      val scale1l   = (env1Mat * 8 - 4).atan.linlin(-4.0.atan: GE, 4.0.atan: GE, 0.5/kernel: GE, 1: GE)
      val scale1l   = (env1Mat * 2 - 1).atan.linLin(-1.0.atan: GE, 1.0.atan: GE, 0.5/kernel: GE, 1: GE)

      val ampMat1l  = env1Mat // .pow(1.0/8)
      val ampMat1   = RepeatWindow(ampMat1l, size = 1, num = kernelS)
      val scale1    = RepeatWindow(scale1l , size = 1, num = kernelS)
      val m1a       = AffineTransform2D.scale(in = m1, widthIn = kernel, heightIn = kernel,
        sx = scale1, sy = scale1, zeroCrossings = zeroCrossings, wrap = 0, rollOff = 0.95) * ampMat1

      val m1ab      = m1a // BufferDisk(m1a)

      (m1ab out 0).poll(Metro(kernelS), "ping")
      val sig             = m1ab // ResizeWindow(m1ab, size = kernelS * frameSize, stop = -(kernelS * (frameSize - 1)))
      val specOut         = ImageFile.Spec(width = kernel, height = kernel, numChannels = 3)
      val tempOutRangeGE0 = Frames(DC(0).take(kernelS * 100))
      val tempOutRangeGE  = if (skipFrames == 0) tempOutRangeGE0 else tempOutRangeGE0 + (skipFrames: GE)
      val dirOut          = file("/") / "data" / "temp" / "affine_debug"
      dirOut.mkdirs()
      val tempOut2        = dirOut / "frame-%d.png"
      ImageFileSeqOut(template = tempOut2, spec = specOut, in = sig, indices = tempOutRangeGE)
    }

    val ctl = Control(cfg)
    Swing.onEDT {
      gui = SimpleGUI(ctl)
    }
    ctl.run(g)
  }
}