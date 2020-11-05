package de.sciss.fscape.tests

import java.awt.geom.AffineTransform

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control
import de.sciss.fscape.{GE, Graph, graph}

import scala.swing.Swing

object AffineTransformTest {
  def main(args: Array[String]): Unit = run()

  def applyLevels(in: GE, lo: Int, hi: Int, hasAlpha: Boolean = false): GE = if (lo == 0 && hi == 255) in else {
    import graph._
    val rgb   = if (hasAlpha) Seq.tabulate(3)(ch => ChannelProxy(in, ch)): GE else in
    val low   = lo / 255.0
    val high  = hi / 255.0
    val p2    = rgb.linLin(low, high, 0, 1)
    val flt   = p2 .max(0).min(1) // .elastic(10)
    val out   = if (hasAlpha) {
      val clipE = flt.elastic(5)
      Seq.tabulate(4)(ch => ChannelProxy(if (ch == 3) in else clipE, ch)): GE
    } else flt
    out
  }

  def applyGamma(in: GE, gamma: Double, hasAlpha: Boolean = false): GE = if (gamma == 1) in else {
    import graph._
    val rgb   = if (hasAlpha) Seq.tabulate(3)(ch => ChannelProxy(in, ch)): GE else in
    val flt   = rgb.pow(1.0 / gamma)
    val out   = if (hasAlpha) {
      val clipE = flt.elastic(1)
      Seq.tabulate(4)(ch => ChannelProxy(if (ch == 3) in else clipE, ch)): GE
    } else flt
    out
  }

  def run(): Unit = {
    val config = Control.Config()
    config.useAsync = false
    //  config.blockSize = 3456
    var gui: SimpleGUI = null
    config.progressReporter = rep => Swing.onEDT(gui.progress = rep.total)
    val ctrl = Control(config)

    val g = Graph {
      import graph._
      // images appear 90 degree rotated clock-wise in gnome/gimp compared to their actual orientation!
  //    val X = javax.imageio.ImageIO.read(tempIn.parent / tempIn.name.format(9948))
  //    require (X.getWidth  == widthIn , s"width is ${X.getWidth}")
  //    require (X.getHeight == heightIn, s"height is ${X.getHeight}")
  //    X.flush()

      val dir0  = {
        val d = userHome / "Documents" / "projects" / "Imperfect"
        if (d.isDirectory) d else file("/data") / "projects" / "Imperfect"
      }
      val tempIn    = dir0 / "photos" / "161008_HH"/ "_MG_%d.JPG"
      // val fIn       = userHome / "Documents" / "projects" / "Imperfect" / "photos" / "raspi" / "manual_gain.jpg"
//      val tempOut   = userHome / "Documents" / "projects" / "Imperfect" / "precious"/ "test-%d.jpg"
      val tempOut   = file("/") / "data" / "temp"/ "test-%d.jpg"
      // val fOut      = userHome / "Documents" / "temp" / "_killme.jpg"
      val widthIn0  = 3456
      val heightIn0 = 5184

      val widthIn   = heightIn0
      val heightIn  = widthIn0
      val widthOut  = 1024
      val heightOut = 1024
      val frameSizeOut  = widthOut.toLong * heightOut

      val cx0       = 1972 // 1947
      val cy0       = 2632 // 2700
      val cxIn      = -cy0
      val cyIn      = widthIn0 - cx0

      val cxOut     = widthOut  * 0.5
      val cyOut     = heightOut * 0.5

      // val radiusMax = Seq(cy, heightIn - cy, cx, widthIn - cx).min
      val diam      = 2400 // 2500 //   2750
      val scale     = widthOut.toDouble / diam

      val rotations = 0 until 360 by 10
      val seqLen    = rotations.size

      val indexIn   = 9948

      require((tempIn.parent / tempIn.name.format(indexIn)).isFile)

      val indicesIn = ValueIntSeq(Seq.fill(seqLen)(indexIn):_ *)
      val img       = ImageFileSeqIn(template = tempIn.toURI, numChannels = 3, indices = indicesIn)

  //    at.translate  (-cx, -cy)
  //    at.rotate     (rot)
  //    at.scale      (scale, scale)
  //    at.translate  (widthOut * 0.5, heightOut * 0.5)

  //    at.translate  (widthOut * 0.5, heightOut * 0.5)

  //    at.translate  (-(cxIn * scale - cxOut), -(cyIn * scale - cyOut))
  //    at.scale      (scale, scale)
  //    at.translate  ( cxIn,  cyIn)
  //    at.rotate     (rot)
  //    at.translate  (-cxIn, -cyIn)

      val transforms = rotations.map { rotDeg =>
        val rot = math.Pi / 180 * rotDeg
        val at = new AffineTransform
        at.preConcatenate(AffineTransform.getRotateInstance(rot, cxIn, cyIn))
        at.preConcatenate(AffineTransform.getTranslateInstance(-cxIn, -cyIn))
        at.preConcatenate(AffineTransform.getScaleInstance(scale, scale))
        at.preConcatenate(AffineTransform.getTranslateInstance(cxOut, cyOut))
        val _mat = new Array[Double](6)
        at.getMatrix(_mat)
        _mat
      }
      val transformsT = transforms.transpose
      println(s"SHAPE: [${transformsT.size}][${transformsT(0).size}]")

      val mat = transformsT.zipWithIndex.map { case (cSeq, _ /* cIdx */) =>
        val cSeqGE = ValueDoubleSeq(cSeq: _*) // .map(x => x: GE).reduce(_ ++ _)
        val res = RepeatWindow(cSeqGE, num = frameSizeOut)
        // Length(res).poll(0, s"coef $cIdx length")
        // res.poll(Metro(frameSizeOut), s"coef $cIdx")
        res // .elastic(frameSizeOut / ctrl.blockSize)
      }

      //    at.concatenate(AffineTransform.getTranslateInstance(-cxIn, -cyIn))
  //    at.scale(scale, scale)
  //    at.translate(-cxIn, -cyIn)
  //    at.concatenate(AffineTransform.getScaleInstance(scale, scale))
      //    at.preConcatenate(AffineTransform.getTranslateInstance(cxOut, cyOut))

      //    at.rotate     (math.Pi/180 * 10, cx, cy)
  //    at.translate  ((widthIn - widthOut) * 0.5, (heightIn - heightOut) * 0.5)
  //    val mat       = new Array[Double](6)
  //    val sin1 = SinOsc(0.011) * math.Pi // 0.99
  //    val sin2 = SinOsc(0.012) * math.Pi // 0.99
  //    val sin3 = SinOsc(0.013) * math.Pi // 0.99
  //    val sin4 = SinOsc(0.014) * math.Pi // 0.99
  //    val sig0      = AffineTransform2D(img, widthIn = width, heightIn = height, widthOut = width, heightOut = height,
  //      m00 = mat(0), m10 = mat(1), m01 = mat(2), m11 = mat(3), m02 = mat(4), m12 = mat(5),
  //      zeroCrossings = 7, wrap = 1)
      val sig0      = AffineTransform2D(img, widthIn = widthIn, heightIn = heightIn, widthOut = widthOut, heightOut = heightOut,
        m00 = mat(0), m10 = mat(1), m01 = mat(2), m11 = mat(3), m02 = mat(4), m12 = mat(5),
        zeroCrossings = 0 /* 4 */ /* 7 */, wrap = 1)
      val sig       = sig0.max(0).min(1) // .elastic(frameSizeOut / ctrl.blockSize)

      val spec  = ImageFile.Spec(width = widthOut, height = heightOut, numChannels = 3, fileType = ImageFile.Type.JPG)
  //    ImageFileOut(file = fOut, spec = spec, in = sig)
      val indicesOut = ValueIntSeq(1 to seqLen: _*)
      ImageFileSeqOut(template = tempOut.toURI, spec = spec, in = sig, indices = indicesOut)

      Progress(in = Frames(ChannelProxy(sig, 0)) / (frameSizeOut * seqLen), Metro(widthOut /* frameSizeOut */))
    }

    Swing.onEDT {
      gui = SimpleGUI(ctrl)
    }

    ctrl.run(g)
//    Await.result(ctrl.status, Duration.Inf)

    // println("Running.")
  }
}
