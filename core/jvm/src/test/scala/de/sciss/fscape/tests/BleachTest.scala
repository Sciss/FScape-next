package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.stream.Control
import de.sciss.fscape.{GE, Graph, graph}
import de.sciss.numbers.Implicits._
import de.sciss.audiofile.{AudioFile, AudioFileSpec}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object BleachTest extends App {
  lazy val g = Graph {
    import graph._
//    val fIn     = file("/") / "data" / "temp" / "sin400_sr441_10s.aif"
    val fIn     = file("/data/projects/Maeanderungen/audio_work/edited/MT-15_HH_T153.wav")
    val specIn  = AudioFile.readSpec(fIn)
    import specIn.{numChannels, sampleRate}
//    val numChannels = 1
    val fOut      = file("/") / "data" / "temp" / "test2.aif"
    val fltLen    = 441
    val feedback  = -48.0.dbAmp // -60.0.dbAmp // -60.0.dbamp
    val clip      =  36.0.dbAmp // 18.0.dbAmp
    val twoWays   = true
    val inverse   = false

    val in        = AudioFileIn(fIn, numChannels = specIn.numChannels)
//    val in        = ChannelProxy(in0, 0)

    def mkBleach(in: GE): GE = {
      val b = Bleach(in = in, filterLen = fltLen, feedback = feedback, filterClip = clip)
      if (inverse) b else in - b
    }

    val sig0 = if (twoWays) {
      Slices(in, ValueLongSeq(specIn.numFrames, 0))
    } else {
      in
    }

    val sig1 = mkBleach(sig0)

    val sig2 = if (twoWays) {
      val rvs = Slices(sig1, ValueLongSeq(specIn.numFrames, 0))
      mkBleach(rvs)
    } else {
      sig1
    }

    val sig = sig2
    val out     = AudioFileOut(sig, fOut, spec = AudioFileSpec(numChannels = numChannels, sampleRate = sampleRate))
    Progress(out / specIn.numFrames.toDouble, Metro(44100))
  }

  val config = Control.Config()
  implicit val ctrl: Control = Control(config)

  val t0 = System.currentTimeMillis()
  ctrl.run(g)
  Await.result(ctrl.status, Duration.Inf)
  val t1 = System.currentTimeMillis()
  println(s"Took ${t1-t0}ms")
}