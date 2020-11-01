package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.{GE, Graph, graph, stream}
import de.sciss.audiofile.AudioFileSpec

object ResampleTest extends App {
  def any2stringadd: Any = ()

  lazy val g1 = Graph {
    import graph._
    val sr    = 44100.0
    val f0    = 100.0 // 441.0
    val in0   = SinOsc(f0/sr)
//    val in0   = WhiteNoise()
    val in    = in0.take(sr.toLong * 10.0)
    val factor = 1.0/32
//    val factor = 1.0
//        val factor = 2.0
    val sig   = Resample(in = in, factor = factor,
      rollOff = 0.70, kaiserBeta = 6.5, zeroCrossings = 5
    )
    // val factorI = (factor * 100).toInt
    val fOut  = userHome / "Documents" / s"resample-test.aif"
    AudioFileOut(file = fOut, spec = AudioFileSpec(sampleRate = sr, numChannels = 1), in = sig)
  }

  lazy val g2 = Graph {
    import graph._
    val sr    = 44100.0
    val in0   = SinOsc(441/sr)
    //    val in0   = WhiteNoise()
    val in    = in0.take(sr.toLong * 10)
    val period = 3200 // 32
    val factor = SinOsc(1.0/period).linExp(-1.0, 1.0, 0.5, 2.0)
    val sig   = Resample(in = in, factor = factor, minFactor = 0.5)
    val fOut  = userHome / "Documents" / "resample_mod.aif"
    AudioFileOut(file = fOut, spec = AudioFileSpec(sampleRate = sr, numChannels = 1), in = sig)
  }

  lazy val g3 = Graph {
    import graph._
    val sr      = 44100.0
    val in0     = SinOsc(220.5/sr)
    val len     = sr.toLong * 10
    val in      = in0 // .take(len)
    val factor  = Line(1.0, 0.1, len)
    val sig0    = Resample(in = in, factor = factor, minFactor = 0.1)
    val sig     = sig0.take(len)
    val fOut    = userHome / "Documents" / "resample_line.aif"
    AudioFileOut(file = fOut, spec = AudioFileSpec(sampleRate = sr, numChannels = 1), in = sig)
  }

  lazy val g4 = Graph {
    import graph._
    val fIn     = userHome / "Pictures" / "naya" / "13557722_10155088508818065_6474655863286963867_n.jpg"
    val w       = 960
    val h       = 960 - 1
    val in      = ImageFileIn(fIn, numChannels = 3)

    def resample(in: GE, x: Double): GE = {
      val sin     = SinOsc(freqN = 1.0/50, phase = Seq[GE](0.0, math.Pi * 1/3, math.Pi * 2/3))
      val factor  = (sin * SinOsc(10.0 / (w * h))).linExp(-1, 1, 1.0/x, x) + 1
      val sig0    = Resample(in = in, factor = factor, minFactor = 1.0/x) // .elastic()
      sig0.take(w * h)
    }

    val r1      = resample(in = in, x = 1.01)
    val tp      = TransposeMatrix(r1, h, w)
    val r2      = resample(in = tp, x = 1.01)
    val sig0    = TransposeMatrix(r2, w, h)
    val sig     = sig0 // .take(w * h)
    val fOut    = userHome / "Documents" / "naya-freq-mode.jpg"
    ImageFileOut(file = fOut, spec = ImageFile.Spec(ImageFile.Type.JPG, width = w, height = h, numChannels = 3), in = sig)
  }

  val ctrl: stream.Control = stream.Control()
  ctrl.run(g3)
}