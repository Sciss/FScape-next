package de.sciss.fscape
package tests

import de.sciss.file._
import de.sciss.fscape.Ops._

object ImageFileOutTest extends App {
  val width   = 1024
  val height  = 768

  val fOut    = args.headOption.fold(userHome / "test.jpg")(file)

  val g = Graph {
    import graph._
    val xSin  = SinOsc(Seq[GE](0.5/width, 1.0/width, 1.5/width)).abs
    val ySin  = SinOsc(0.5/(height * width))
    val amp   = xSin * ySin
    val spec  = ImageFile.Spec(width = width, height = height, numChannels = 3 /* 1 */,
      fileType = ImageFile.Type.JPG, sampleFormat = ImageFile.SampleFormat.Int8,
      quality = 100)
    ImageFileOut(file = fOut.toURI, spec = spec, in = amp)
  }

  val config = stream.Control.Config()
  config.blockSize  = 599   // a prime number unrelated to `width` and `height`, for testing
  config.useAsync   = false // for debugging
  val ctrl  = stream.Control(config)

  ctrl.run(g)

//  Swing.onEDT {
//    SimpleGUI(ctrl)
//  }

  println("Running.")
}