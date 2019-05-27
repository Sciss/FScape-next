package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.{Graph, graph, stream}
import de.sciss.kollflitz
import org.rogach.scallop.{ScallopConf, ScallopOption => Opt}

import scala.swing.Swing

object GimpSlurTest {
  case class Config(fIn: File = file("in"), fOut: File = file("out"),
                    narrow: Double = 0.8, randomization: Double = 0.2, repeat: Int = 4)

  def main(args: Array[String]): Unit = {
    object parse extends ScallopConf(args) {
      printedName = "Neural"
      version(printedName)

      val default = Config()

      val input : Opt[File] = opt(required = true, descr = "Image input file.")
      val output: Opt[File] = opt(required = true, descr = "Image output file.")
      mainOptions = Seq(input, output)

      val narrow: Opt[Double] = opt(short = 'w', default = Some(default.narrow),
        descr = s"Narrowness of the 'beam', 0 to 1 (default ${default.narrow}).",
        validate = (v: Double) => v >= 0 && v <= 1  // "Must be >= 0 and <= 1"
      )
      val randomization: Opt[Double] = opt(default = Some(default.randomization),
        descr = s"Probability of pixels being slurred, 0 to 1 (default ${default.randomization}).",
        validate = v => v >= 0 && v <= 1  // "Must be >= 0 and <= 1"
      )
      val repeat: Opt[Int] = opt(short = 'n', default = Some(default.repeat),
        descr = s"Number of recursive repetitions, 1 or greater (default ${default.repeat})"
      )
      verify()
      val config = Config(fIn = input(), fOut = output(), narrow = narrow(),
        randomization = randomization(), repeat = repeat())
    }

    run(parse.config)
  }

  def run(config: Config): Unit = {
    val specIn  = ImageFile.readSpec(config.fIn)
    import specIn.{height, numChannels, width}

    val g = Graph {
      import graph._
      val imgIn   = ImageFileIn(config.fIn, numChannels = numChannels)
      val pTL     = (1.0 - config.narrow) * 0.5 * config.randomization
      val pT      = config.narrow * config.randomization
      val pTR     = pTL
      val pC      = 1.0 - config.randomization
      val wv      = Vector(pTL, pT, pTR, 0.0, pC, 0.0, 0.0, 0.0, 0.0)
      import kollflitz.Ops._
      val wvi     = wv.integrate
      assert(wvi.last == 1.0 && wvi.size == 9)
      val kernel  = ValueDoubleSeq(wvi: _*).take(wvi.size)
      val slur    = GimpSlur(imgIn, width = width, height = height,
        kernel = kernel, kernelWidth = 3, kernelHeight = 3,
        repeat = config.repeat)
      val tpeOut  = if (config.fOut.extL == "png") ImageFile.Type.PNG else ImageFile.Type.JPG
      val specOut = specIn.copy(fileType = tpeOut, sampleFormat = ImageFile.SampleFormat.Int8)
      ImageFileOut(file = config.fOut, spec = specOut, in = slur)
    }

    val sCfg      = stream.Control.Config()
    sCfg.useAsync = false
    val ctrl      = stream.Control(sCfg)

    val t0 = System.currentTimeMillis()
    ctrl.run(g)
    import ctrl.config.executionContext
    ctrl.status.foreach { _ =>
      val t1 = System.currentTimeMillis()
      println(s"Took ${t1-t0} ms.")
      sys.exit()
    }

    Swing.onEDT {
      SimpleGUI(ctrl)
    }

    println("Running.")
  }
}