package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.{Graph, graph, stream}
import de.sciss.synth.io.AudioFileSpec

object SegModPhasorTest extends App {
  private def any2stringadd: Any = ()

  lazy val g1 = Graph {
    import graph._
    val sz      = 400
    val periods = Seq(60, 120, 60, 120)
    val freqN   = ValueDoubleSeq(periods.map(1.0 / _): _*)
    val sh      = SegModPhasor(freqN, 0.25)
//    val sig     = (sh * 2 * math.Pi).sin  // sine
//    val sig     = (sh * -4 + 2).fold(-1, 1) // triangle
//    val sig     = (sh < 0.5) * 2 - 1 // pulse
//    val sig     = sh * 2 - 1 // sawtooth (1)
//    val sig     = ((sh + 0.25) % 1.0) * 2 - 1 // sawtooth (2)
    val sig     = ((sh + 0.5) % 1.0) * 2 - 1 // sawtooth (3)
//    val sig     = sh * DC(0.0) // silence
    Plot1D(sig, size = sz, label = "seg-mod")
  }

  lazy val g2 = Graph {
    import graph._
    val periods = Vector.fill(1200)(2)
//    val periods = Vector(
//      1024, 512, // 1024,
//      1024,
////      1024,
////      1024, 1024, 1024, 1024, 1024, 1024, 1024, 1024, 1024, 1024, 1024, 1024, 618, 1024, 1024, 1024, 1024, 1024, 1024, 1024, 1024, 1024, 1024, 1024, 1024
//    )
    val freqN   = ValueDoubleSeq(periods.map(1.0 / _): _*)
    val phase0  = 0.25
    val sh      = SegModPhasor(freqN, phase0)
    val sig     = sh // ((sh + phase0) * (2 * math.Pi)).sin  // sine
//    val sig     = (sh * (2 * math.Pi)).sin  // sine
    /* val frames  = */ AudioFileOut(sig, file("/data/temp/foo.aif"), AudioFileSpec(numChannels = 1, sampleRate = 44100))
    // Progress(frames / periods.last, Metro(44100))
  }
  val ctl = stream.Control()

//  de.sciss.fscape.showStreamLog = true

  ctl.run(g1)
}