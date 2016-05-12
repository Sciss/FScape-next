package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, GraphDSL, Sink}
import de.sciss.file.File
import de.sciss.synth.io.AudioFileSpec

object DiskOut {
  def apply(file: File, spec: AudioFileSpec, in: Signal[Double])(implicit b: GraphDSL.Builder[NotUsed]): Unit = {
    val sink = new AudioFileSink(file, spec)
    // import GraphDSL.Implicits._
    // val sinkG = Sink.fromGraph(sink)
    // in.asInstanceOf[Flow[Double, Double, NotUsed]] ~> sinkG
    in.to(sink)
    b.add(sink)
  }
}