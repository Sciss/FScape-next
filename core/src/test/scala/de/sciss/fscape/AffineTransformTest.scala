package de.sciss.fscape

import java.awt.geom.AffineTransform

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control

import scala.swing.Swing

object AffineTransformTest extends App {
  val g = Graph {
    import graph._
    val fIn       = userHome / "Documents" / "projects" / "Imperfect" / "photos" / "raspi" / "manual_gain.jpg"
    val fOut      = userHome / "Documents" / "temp" / "_killme.jpg"
    val width     = 1024
    val height    = 1024
    val img       = ImageFileIn(file = fIn, numChannels = 3)
    val at        = AffineTransform.getRotateInstance(math.Pi/180 * 10, width * 0.5, height * 0.5)
    // at.setToIdentity()
    // at.setToScale(2.0, 2.0)
    at.scale(0.667, 0.667)
    val mat       = new Array[Double](6)
    at.getMatrix(mat)
    val sig0      = AffineTransform2D(img, widthIn = width, heightIn = height, widthOut = width, heightOut = height,
      m00 = mat(0), m10 = mat(1), m01 = mat(2), m11 = mat(3), m02 = mat(4), m12 = mat(5),
      zeroCrossings = 7, wrap = 1)
    val sig       = sig0.max(0).min(1)

    val spec  = ImageFile.Spec(width = width, height = height, numChannels = 3, fileType = ImageFile.Type.JPG)
    ImageFileOut(file = fOut, spec = spec, in = sig)
  }

  val config = Control.Config()
  config.useAsync = false
  val ctrl = Control(config)

  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}