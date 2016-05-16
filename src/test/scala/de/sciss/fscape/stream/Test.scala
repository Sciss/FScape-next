package de.sciss.fscape.stream

import akka.actor.ActorSystem
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, ClosedShape}
import de.sciss.file._
import de.sciss.synth.io.AudioFileSpec

object Test extends App {
  showStreamLog = true

  val fIn   = userHome / "Music" / "work" / "mentasm-199a3aa1.aif"
  // val fIn   = userHome / "Music" / "work" / "B19h39m45s23jan2015.wav"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"

  implicit val ctrl = Control(600)

  val graph = GraphDSL.create() { implicit b =>
    val in      = DiskIn(file = fIn)
    val size    = b.add(Source.single(BufI(1024))).out
    val padding = b.add(Source.single(BufI(   0))).out
    val fft     = Real1FFT(in, size = size, padding = padding)
    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = fft)
    ClosedShape
  }

//  val graph = GraphDSL.create() { implicit b =>
//    val in  = Source.unfoldResource[Double, Iterator[Int]](
//      () => Iterator(1 to 10: _*), it => if (it.hasNext) Some(it.next().toDouble) else None, _ => ())
//    import GraphDSL.Implicits._
//    val fft = in.importAndGetPort(b) // Real1FFT(in, size = 1024)
//    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = fft)
//    ClosedShape
//  }

//  val graph1 = GraphDSL.create() { implicit b =>
//    val in  = Source.fromIterator(() => (1 to 10).iterator.map(_.toDouble))
//    val out = Sink.foreach[Double] { d =>
//      println(s"elem: $d")
//    }
//    import GraphDSL.Implicits._
//    val inOutlet = b.add(in).out
//    inOutlet ~> out
//    ClosedShape
//  }

  implicit val system = ActorSystem()
  implicit val mat    = ActorMaterializer(
    ActorMaterializerSettings(system)
//      .withInputBuffer(
//        initialSize = 1024,
//        maxSize     = 1024)
  )

  val rg  = RunnableGraph.fromGraph(graph)
  val res = rg.run()
  println("Running.")
}