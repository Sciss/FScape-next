package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

/** Tests new binary op termination */
object BinaryOpTest extends App {
  val config = stream.Control.Config()
  config.blockSize  = 1024
//  config.useAsync   = false // for debugging
  val ctrl = stream.Control(config)

  lazy val g = Graph {
    import graph._

    val width     = 2000
    val height    = 2000
    val frameSize = width * height

    def normalize(in: GE, headroom: GE = 1): GE = {
      val max       = RunningMax(in.abs).last
      val gain      = max.reciprocal * headroom
      gain.ampdb.roundTo(0.01).poll(0, "gain [dB]")
      // Plot1D(in, width * height)
      // in.poll(1.0/32, label = "test")
      val buf       = BufferDisk(in)
      buf * gain
    }

    def extractBrightness(in: GE): GE = {
      val r   = ChannelProxy(in, 0)
      val g   = ChannelProxy(in, 1)
      val b   = ChannelProxy(in, 2)
      (0.299 * r.squared + 0.587 * g.squared + 0.114 * b.squared).sqrt
      //        (r.squared * 0.299 + g.squared * 0.587 + b.squared * 0.114).sqrt
    }

    val in    = WhiteNoise(Seq.fill[GE](3)(0.5)).take(frameSize * 10)
    val bri   = extractBrightness(in)
    val last  = bri.takeRight(frameSize)
    val sig   = normalize(last)

//    sig.poll(1.0/500, "sum")
//    Length(sig).poll(0, "length")

    val fOut  = userHome / "Documents" / "temp" / "out_median.jpg"

    val spec  = ImageFile.Spec(width = width, height = height, numChannels = 1 /* 3 */,
      fileType = ImageFile.Type.JPG /* PNG */, sampleFormat = ImageFile.SampleFormat.Int8,
      quality = 100)
    ImageFileOut(file = fOut, spec = spec, in = sig)
  }

  ctrl.run(g)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}