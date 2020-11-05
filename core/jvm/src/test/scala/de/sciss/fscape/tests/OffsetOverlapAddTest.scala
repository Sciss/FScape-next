package de.sciss.fscape.tests

import de.sciss.audiofile.AudioFileSpec
import de.sciss.file._
import de.sciss.fscape.{Graph, graph, stream}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object OffsetOverlapAddTest extends App {
  val out = file("/") / "data" / "temp" / "_killme.aif"

  val config = stream.Control.Config()
  config.blockSize = 1024
  config.useAsync = false // for debugging
  val ctrl = stream.Control(config)

  lazy val g = Graph {
    import graph._
    val stepSize      = 100
    val overlap       = 2
    val winSize       = stepSize * overlap
    val impulse       = Metro(winSize).take(3000)   // 'distribute diracs'
    val offset        = WhiteNoise(4).floor // XXX TODO --- a `.toInt` would be useful
    val minOffset     = -4
    val sig           = OffsetOverlapAdd(in = impulse, size = winSize, step = stepSize,
      offset = offset, minOffset = minOffset)
    AudioFileOut(file = out.toURI, spec = AudioFileSpec(sampleRate = 44100.0, numChannels = 1), in = sig)
  }

  // showStreamLog = true

  ctrl.run(g)

//  Swing.onEDT {
//    SimpleGUI(ctrl)
//  }

  println("Running.")
  Await.result(ctrl.status, Duration.Inf)
}