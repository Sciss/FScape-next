package de.sciss.fscape

import de.sciss.file._
import de.sciss.synth.io.AudioFileSpec

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object FreqFilterTest extends App {
  val g = Graph {
    import graph._
    val in    = Impulse(0)
    val sr    = 44100.0
    val freq  = 1000.0
    val freqN = freq/sr
    val len   = 65536
    val sig1  = HPF(in, freqN).take(len)
    val sig2  = LPF(in, freqN).take(len)
    AudioFileOut(userHome / "hpf-test.aif", AudioFileSpec(numChannels = 1, sampleRate = sr), in = sig1)
    AudioFileOut(userHome / "lpf-test.aif", AudioFileSpec(numChannels = 1, sampleRate = sr), in = sig2)
  }

  val ctl = stream.Control()
  ctl.run(g)
  Await.result(ctl.status, Duration.Inf)
  sys.exit()
}