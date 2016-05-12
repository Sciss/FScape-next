package de.sciss.fscape.stream

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink, Source}
import de.sciss.file._
import de.sciss.synth.io.AudioFileSpec

object Test extends App {
  val fIn   = userHome / "Music" / "work" / "mentasm-199a3aa1.aif"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"

//  val graph = GraphDSL.create() { implicit b =>
//    val in  = DiskIn(file = fIn)
//    val fft = in // Real1FFT(in, size = 1024)
//    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = fft)
//    ClosedShape
//  }

  val graph = GraphDSL.create() { implicit b =>
    val in  = Source.fromIterator(() => (1 to 10).iterator.map(_.toDouble))
    // b.add(in)
    val out = Sink.foreach[Double] { d =>
      println(s"elem: $d")
    }
    // b.add(out)
    // in.to(out)
    import GraphDSL.Implicits._
    in ~> out
    ClosedShape
  }

  implicit val system = ActorSystem()
  implicit val mat    = ActorMaterializer()
  val rg = RunnableGraph.fromGraph(graph)
  rg.run()
  println("Running.")
}