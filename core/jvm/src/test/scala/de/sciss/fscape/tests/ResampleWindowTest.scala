package de.sciss.fscape
package tests

import de.sciss.audiofile.AudioFileSpec
import de.sciss.file._
import de.sciss.fscape.Ops._

object ResampleWindowTest extends App {
  lazy val g1 = Graph {
    import graph._
    val sr      = 44100.0
    val in0     = SinOsc(220.5/sr)
    val len     = sr.toLong * 10
    val in      = in0 // .take(len)
    val factor  = Line(1.0, 0.1, len)
    val sig0    = ResampleWindow(in = in, size = 1, factor = factor, minFactor = 0.1)
    val sig     = sig0.take(len)
    val fOut    = userHome / "Documents" / "resample_w_line.aif"
    AudioFileOut(file = fOut.toURI, spec = AudioFileSpec(sampleRate = sr, numChannels = 1), in = sig)
  }

  lazy val g2 = Graph {
    import graph._
    val sr      = 44100.0
    val in      = SinOsc(220.5/sr)
    val len     = sr.toLong * 10
    val factor  = 2.0
    val sig0    = ResampleWindow(in = in, size = 50, factor = factor)
    val sig     = sig0.take(len)
    val fOut    = userHome / "Documents" / "resample_w.aif"
    AudioFileOut(file = fOut.toURI, spec = AudioFileSpec(sampleRate = sr, numChannels = 1), in = sig)
  }

  lazy val g3 = Graph {
    import graph._
    val sr      = 44100.0
    val in1     = SinOsc(220.5/sr)
    val in2     = SinOsc(666.0/sr)
    val len     = sr.toLong * 10
    val factor  = Line(2.0, 0.1, len)
    val in      = ZipWindow(in1, in2)
    val sig0    = ResampleWindow(in = in, size = 2, factor = factor, minFactor = 0.1)
    val sig     = sig0.take(len * 2)  // XXX TODO --- should integrate the `Line`
    val unzip   = UnzipWindow(sig)
    val sig1    = ChannelProxy(unzip, 0)
    val sig2    = ChannelProxy(unzip, 1)
    val fOut1   = userHome / "Documents" / "resample_w_line1.aif"
    val fOut2   = userHome / "Documents" / "resample_w_line2.aif"
    AudioFileOut(file = fOut1.toURI, spec = AudioFileSpec(sampleRate = sr, numChannels = 1), in = sig1)
    AudioFileOut(file = fOut2.toURI, spec = AudioFileSpec(sampleRate = sr, numChannels = 1), in = sig2)
  }

  lazy val g4 = Graph {
    val width   = 1024
    val height  = 1024
    val indices = 1340 to 1350
    val factor  = 4 // 2
    val indicesOut  = 1 to (indices.size * factor)
    val fIn     = file("/") / "data" / "projects" / "Imperfect" / "site-2out_sel" / "frame-%d.jpg"
    val dOut    = file("/") / "data" / "temp" / "test-rsmp"
    dOut.mkdirs()
    val fOut    = dOut / "frame-%d.jpg"

    import graph._
    val idxSeq  = indices.map(x => x: GE).reduce(_ ++ _)

    val frameSize = width * height
    val in      = ImageFileSeqIn(template = fIn.toURI, numChannels = 3, indices = idxSeq)
    val sig0    = ResampleWindow(in = in, size = frameSize, factor = factor)
    val sig     = sig0.max(0.0).min(1.0) // .take(len)

    val idxSeqOut = indicesOut.map(x => x: GE).reduce(_ ++ _)
    val spec    = ImageFile.Spec(width = width, height = height, numChannels = 3 /* 1 */,
      fileType = ImageFile.Type.JPG, sampleFormat = ImageFile.SampleFormat.Int8)
    ImageFileSeqOut(template = fOut.toURI, spec = spec, indices = idxSeqOut, in = sig)
  }

  val ctrl: stream.Control = stream.Control()
  ctrl.run(g4)
}