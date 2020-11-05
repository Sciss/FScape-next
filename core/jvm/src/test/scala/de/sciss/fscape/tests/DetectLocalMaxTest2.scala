package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.stream.Control
import de.sciss.fscape.{Graph, graph}

object DetectLocalMaxTest2 extends App {
  val g = Graph {
    import graph._
    val f           = file("/data/projects/Shouldhalde/audio_work/shouldhalde-200318- 13_18_500corr4096x1x3dc.aif")
    val inCorr      = AudioFileIn(f.toURI, numChannels = 2)
    val timeStep    = 1
    val segmMaxDur  = 1.0
    val corrWin     = (segmMaxDur * 44100.0 / timeStep).round.toInt.max(1)
    val mono        = inCorr.out(0)
    val localMax    = DetectLocalMax(mono, corrWin)
    Length(localMax).poll("test")
  }

  val config        = Control.Config()
  config.useAsync   = false
  config.blockSize  = 1024    // problem is independent of block-size
  println("--1")
  Control(config).run(g)
  println("--2")
}